package com.games.soywiz.korgekingdom

import com.soywiz.korio.error.invalidOp
import com.soywiz.korio.ext.db.redis.Redis
import com.soywiz.korio.ext.db.redis.hget
import com.soywiz.korio.ext.db.redis.hset
import com.soywiz.korio.ext.db.redis.key
import com.soywiz.korio.inject.Prototype
import com.soywiz.korio.util.Extra
import java.util.*
import kotlin.collections.LinkedHashSet

@Prototype
class Server(
        private val redis: Redis
) {
    class Room(val name: String) {
        val clients = LinkedHashSet<Channel>()
    }

    private val clients = LinkedHashSet<Channel>()
    private val logins = redis.key("korge_logins")

    val defaultRoom = Room("default")

    val server = this
    val roomsByName = hashMapOf<String, Room>(defaultRoom.name to defaultRoom)

    // Extra properties for Channel
    var Channel.userName: String by Extra.Property("userName") { "" }
    var Channel.entityId: Long by Extra.Property("entityId") { 0L }
    var Channel.room: Room by Extra.Property("room") { defaultRoom }

    var lastEntityId = 0L

    suspend fun handleClient(channel: Channel) {
        channel.process()
    }

    suspend fun Channel.process() {
        val user = login()
        try {
            clients += this
            userName = user
            entityId = lastEntityId++
            room = defaultRoom
            room.clients += this
            send(RoomPackets.Server.Joined(self = entityId, name = room.name))
            processIngame()
        } finally {
            room.clients -= this
            clients -= this
        }
    }

    suspend fun Channel.processIngame() {
        while (true) {
            val it = read()
            when (it) {
                is ChatPackets.Client.Say -> {
                    server.clients.send(ChatPackets.Server.Said(userName, it.msg))
                }
                is EntityPackets.Client.Move -> {
                    room.clients.send(EntityPackets.Server.Moved(entityId, it.x, it.y))
                }
                else -> {
                    invalidOp("Unknown packet said")
                }
            }
        }
    }

    suspend fun Channel.login(): String {
        val challenge = UUID.randomUUID().toString()
        send(Login.Server.Challenge(challenge))
        val req = wait<Login.Client.Request>()

        try {
            val user = req.user
            val password = logins.hget(user) ?: invalidOp("Can't find user '$user'")
            val expectedHash = Login.Server.Challenge.hash(challenge, password)
            if (req.challengedHash != expectedHash) invalidOp("Invalid challenge")
            send(Login.Server.Result(true, "ok"))
            return user
        } catch (e: Throwable) {
            send(Login.Server.Result(false, e.message ?: "error"))
            throw e
        }
    }

    suspend fun register(user: String, password: String, notify: Channel? = null) {
        logins.hset(user, password)
    }
}
