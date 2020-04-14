package com.authority.domain

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.authority.domain.ResourceEntity.{Command, Query, Resource, Update}

object ResourceEntity{
  /**
   * 命令接口
   */
  sealed trait Command
  case class Update(name:String,src:String) extends Command
  case class Query(replyTo:ActorRef[Resource]) extends Command
  case class Resource(id:Long,name:String,src:String)

  def apply(id:Long,name: String, src: String): Behavior[Command] =
    Behaviors.setup(context => new ResourceEntity(id,name,src,context))
}

/**
 * 资源实体
 */
class ResourceEntity(id:Long,var name:String,var src:String,context: ActorContext[Command]) extends AbstractBehavior[Command](context){

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Update(name, src) =>{
        context.log.info(s"修改资源请求，$name,$src")
        this.name = name
        this.src = src;
        this
      }
      case Query(replyTo)=>{
        context.log.info(s"查询请求，$replyTo")
        replyTo ! Resource(id,name,src)
        this
      }

    }
  }
}
