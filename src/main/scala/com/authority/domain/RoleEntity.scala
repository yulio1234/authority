package com.authority.domain

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.authority.domain.RoleEntity.{BindResource, BoundResource, Close, Closed, Command, Event, Failure, Open, Opened, Role, SaveName, SavedName, Success, UnbindResource, UnboundResource}

import scala.concurrent.duration._
/**
 * 角色实体
 * 使用了有限状态机，并且所有操作都是无副作用操作
 */
object RoleEntity {
  //分片实体键
  val entityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("role")
  trait Result //返回结果
  case object Success extends Result //成功
  case object Failure extends Result //失败

  sealed trait Command //命令
  sealed trait Event //事件

  final case class Open(replyTo: ActorRef[Result]) extends Command //开启权限命令
  final case class Close(replyTo: ActorRef[Result]) extends Command //关闭权限命令

  final case class SaveName(name:String,replyTo: ActorRef[Result]) extends Command
  final case object Opened extends Event //开启权限事件
  final case object Closed extends Event //关闭权限事件

  //绑定资源命令
  final case class BindResource(resourceId:String,resource: ActorRef[ResourceEntity.Command],replyTo: ActorRef[Result]) extends Command
  //解除绑定资源命令
  final case class UnbindResource(resourceId:String,replyTo: ActorRef[Result]) extends Command
  //绑定资源事件
  final case class BoundResource(resourceId:String,resource: ActorRef[ResourceEntity.Command]) extends Event
  //解除接触绑定事件
  final case class UnboundResource(resourceId:String) extends Event
  final case class SavedName(name:String) extends Event

  //实体内的状态接口
  sealed trait Role extends CborSerializable {
    //应用命令
    def applyCommand(cmd: Command): ReplyEffect[Event,Role]
    //应用事件
    def applyEvent(event: Event): Role
  }
  def init(system: ActorSystem[Nothing]): Unit = {
    ClusterSharding(system).init(Entity(entityKey) { entityContext =>
      RoleEntity(entityContext.entityId)
    }.withRole("write-model"))
  }
  //创建方法
  def apply(roleId: String): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.withTimers {
      timers =>
        new RoleEntity("default",timers, context).build(roleId)
    }
  }

}
class RoleEntity(var name:String,timers:TimerScheduler[Command],context: ActorContext[Command]){

  /**
   * 构建角色状态
   * @return
   */
  def build(roleId:String): Behavior[Command] = EventSourcedBehavior
    .withEnforcedReplies[Command, Event, Role](PersistenceId(RoleEntity.entityKey.name,roleId), ClosedRole, (state, cmd) => state.applyCommand(cmd), (state, event) => state.applyEvent(event))
    .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
    .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  /**
   * 开启的权限实体
   * @param resources
   */
  private case class OpenedRole(var name:String,resources:Map[String,ActorRef[ResourceEntity.Command]]) extends Role{

    /**
     * 处理命令并序列化
     * @param cmd
     * @return
     */
    override def applyCommand(cmd: Command): ReplyEffect[Event, Role] = cmd match {
      case SaveName(name, replyTo) =>
        Effect.persist(SavedName(name)).thenReply(replyTo)(_=>Success)
      case BindResource(resourceId,resource,replyTo) =>
        //验证逻辑
        if (resources.contains(resourceId)) {
          Effect.unhandled.thenReply(replyTo)(_=>Failure)
        }else{
          //转换成事件，进行序列化
          Effect.persist(BoundResource(resourceId,resource)).thenReply(replyTo)(_=> Success)
        }
      case UnbindResource(resourceId,replyTo) =>
        Effect.persist(UnboundResource(resourceId)).thenReply(replyTo)(_ => Success)
        //关闭权限
      case Close(replyTo) =>  Effect.persist(Closed).thenReply(replyTo)(_ => Success)
    }

    override def applyEvent(event: Event): Role = event match {
      case SavedName(newName)=>
        copy(newName,resources)
        //序列化绑定事件之后，新增角色
      case BoundResource(resourceId, resource) =>
        copy(name,resources + (resourceId -> resource))
        //序列化解除绑定事件之后，删除角色
      case UnboundResource(resourceId) =>
        copy(name,resources - resourceId)
        //将角色状态转换为关闭
      case Closed => ClosedRole
    }
  }

  /**
   * 关闭的权限实体
   */
  private case object ClosedRole extends Role {
    //不做任何操作，直接返回失败
    override def applyCommand(cmd: Command): ReplyEffect[Event, Role] = cmd match {
      case BindResource(resourceId, resource, replyTo) =>Effect.unhandled.thenReply(replyTo)(_=>Failure)
      case UnbindResource(resourceId, replyTo) =>Effect.unhandled.thenReply(replyTo)(_=>Failure)
      case Open(replyTo) => Effect.persist(Opened).thenReply(replyTo)(_=> Success)
    }
    //返回当前对象
    override def applyEvent(event: Event): Role = event match {
      case BoundResource(resourceId, resource) => this
      case UnboundResource(resourceId) => this
    }
  }
}
