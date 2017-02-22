package com.games.soywiz.korgekingdom

import com.soywiz.korio.async.EventLoop
import com.soywiz.korio.async.spawnAndForget
import com.soywiz.korio.ext.db.redis.Redis
import com.soywiz.korio.inject.AsyncInjector
import com.soywiz.korio.vfs.ResourcesVfs
import io.vertx.core.Vertx

fun main(args: Array<String>) = EventLoop.main {
    val vertx = Vertx.vertx()
    val resources = ResourcesVfs
    val serverInjector = AsyncInjector().map(Redis(listOf("127.0.0.1:6379")))
    val server = serverInjector.get<ServerHandler>()

    vertx.createHttpServer()
            .websocketHandler { ws ->
                spawnAndForget {
                    server.handleClient(VertxClientChannel(ws))
                }
            }
            .requestHandler { req ->
                spawnAndForget {
                    req.response().end(resources["ws.html"].readString())
                }
            }
            .listen(8080) {
                println("Listening at 8080")
            };
}
