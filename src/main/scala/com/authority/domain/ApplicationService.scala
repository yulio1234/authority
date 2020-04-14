package com.authority.domain

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.authority.domain.ApplicationService.{Command, OpenRole, WrappedRoleResult}
import com.authority.domain.RoleEntity.{Open, Result}

object ApplicationService {
  trait Command
  case class OpenRole(id:String,name:String) extends Command
  case class WrappedRoleResult(result: Result) extends Command

}

/**
 * 应用
 */
class ApplicationService(context: ActorContext[Command]) {

  //启动角色分片实体
  RoleEntity.init(context.system)
  private val adapter: ActorRef[Result] = context.messageAdapter(WrappedRoleResult)

  private val sharding: ClusterSharding = ClusterSharding(context.system)
 private def application(): Behavior[Command] = Behaviors.receiveMessage[Command]{
   case OpenRole(id,name) =>
     val roleActor = sharding.entityRefFor(RoleEntity.entityKey, id)
     roleActor ! Open(adapter)
     Behaviors.same
   case WrappedRoleResult(result) =>
     result match {
       case RoleEntity.Failure => context.log
       case RoleEntity.Success =>
       case _ =>
     }



 }
}
