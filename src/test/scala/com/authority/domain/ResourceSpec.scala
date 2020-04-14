package com.authority.domain

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.authority.domain.ResourceEntity.{Query, Resource, Update}
import org.scalatest.WordSpecLike

class ResourceSpec  extends ScalaTestWithActorTestKit with WordSpecLike{
  "一个资源实体" must{
        val resourceActor = spawn(ResourceEntity(1, "test1", "www.baidu.com"))
        "可有修改资源名字和地址" in{
          resourceActor ! Update("test2","www.jd.com")
        }
        "可以查询资源的名称和地址" in{
          val requestActor = createTestProbe[Resource]
          resourceActor ! Query(requestActor.ref)
          requestActor.expectMessage(Resource(1,"test1","www.baidu.com"))
        }
  }

}
