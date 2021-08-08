package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashSet

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Locations) {
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header("MyCustomHeader")
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60) // デフォルトでは disable (null)
        timeout = Duration.ofSeconds(15)
        // disable する場合は最大値 Long.MAX_VALUE を指定
        // frame がこのサイズを超えるとコネクションが切断される
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CallLogging)

    routing {
        webSocket("/chat"){
            val clients = Collections.synchronizedSet(LinkedHashSet<ChatClient>())
            val client = ChatClient(this)
            clients += client
            call.application.environment.log.info("${client.name}: connected.")
            try {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            for (cl in clients) {
                                cl.session.outgoing.send(Frame.Text("${client.name}: $text"))
                            }
                        }
                        is Frame.Binary -> {
                            for (cl in clients) {
                                cl.session.outgoing.send(frame)
                            }
                        }
                    }
                }
            }
            catch(e: ClosedReceiveChannelException){
                call.application.environment.log.info("${client.name}: ${e.message}")
            }
            finally {
                clients -= client
                for(cl in clients){
                    cl.session.outgoing.send(Frame.Text("${client.name} 退出"))
                }
            }
        }
    }
}

class ChatClient(val session: DefaultWebSocketSession){
    companion object { var lastId = AtomicInteger(0) }
    val id = lastId.getAndIncrement()
    val name = "user$id"
}
