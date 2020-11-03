package com.hexagonkt.http.server

import com.hexagonkt.logging.Logger
import com.hexagonkt.helpers.*
import com.hexagonkt.helpers.Jvm.charset
import com.hexagonkt.helpers.Jvm.cpuCount
import com.hexagonkt.helpers.Jvm.hostname
import com.hexagonkt.helpers.Jvm.ip
import com.hexagonkt.helpers.Jvm.name
import com.hexagonkt.helpers.Jvm.version
import com.hexagonkt.helpers.Jvm.locale
import com.hexagonkt.helpers.Jvm.timezone
import com.hexagonkt.http.Protocol.HTTP2
import com.hexagonkt.http.Protocol.HTTP
import com.hexagonkt.injection.InjectionManager.inject
import com.hexagonkt.injection.InjectionManager.injectOrNull

import java.lang.Runtime.getRuntime
import java.lang.management.ManagementFactory.getMemoryMXBean
import java.lang.management.ManagementFactory.getRuntimeMXBean
import com.hexagonkt.helpers.Ansi.BLACK_FG
import com.hexagonkt.helpers.Ansi.BLUE_FG
import com.hexagonkt.helpers.Ansi.BOLD_ON
import com.hexagonkt.helpers.Ansi.CYAN_FG
import com.hexagonkt.helpers.Ansi.DEFAULT_FG
import com.hexagonkt.helpers.Ansi.MAGENTA_FG
import com.hexagonkt.helpers.Ansi.RESET

/**
 * A server that listen to HTTP connections on a port and address and route requests using a
 * router.
 */
data class Server(
    private val adapter: ServerPort = inject(),
    private val router: Router,
    val settings: ServerSettings = ServerSettings()
) {

    private val banner: String = """
    $CYAN_FG          _________
    $CYAN_FG         /         \
    $CYAN_FG        /   ____   /
    $CYAN_FG       /   /   /  /
    $CYAN_FG      /   /   /__/$BLUE_FG   /\$BOLD_ON    H E X A G O N$RESET
    $CYAN_FG     /   /$BLUE_FG          /  \$DEFAULT_FG        ___
    $CYAN_FG     \  /$BLUE_FG   ___    /   /
    $CYAN_FG      \/$BLUE_FG   /  /   /   /$CYAN_FG$BOLD_ON    T O O L K I T$RESET
    $BLUE_FG          /  /___/   /
    $BLUE_FG         /          /
    $BLUE_FG         \_________/
    $RESET
    """.trimIndent()

    private val log: Logger = Logger(this::class)

    val contextRouter: Router by lazy {
        if (settings.contextPath.isEmpty())
            router
        else
            Router { path(settings.contextPath, router) }
    }

    /**
     * Creates a server with a router. It is a combination of [Server] and [Router].
     *
     * @param adapter The server engine.
     * @param settings Server settings. Port and address will be searched in this map.
     * @param block Router's setup block.
     * @return A new server with the built router.
     */
    constructor(
        adapter: ServerPort = inject(),
        settings: ServerSettings = injectOrNull() ?: ServerSettings(),
        block: Router.() -> Unit):
            this(adapter, Router(block), settings)

    val runtimePort
        get() = if (started()) adapter.runtimePort() else error("Server is not running")

    val portName: String = adapter.javaClass.simpleName

    fun started(): Boolean = adapter.started()

    fun start() {
        getRuntime().addShutdownHook(
            Thread (
                {
                    if (started ())
                        adapter.shutdown ()
                },
                "shutdown-${settings.bindAddress.hostName}-${settings.bindPort}"
            )
        )

        adapter.startup (this)
        log.info { "Server started\n${createBanner()}" }
    }

    fun stop() {
        adapter.shutdown ()
        log.info { "Server stopped" }
    }

    private fun createBanner(): String {
        val heap = getMemoryMXBean().heapMemoryUsage
        val jvmMemory = "%,d".format(heap.init / 1024)
        val usedMemory = "%,d".format(heap.used / 1024)
        val bootTime = "%01.3f".format(getRuntimeMXBean().uptime / 1e3)
        val bindAddress = settings.bindAddress
        val protocol = settings.protocol
        val hostName = if (bindAddress.isAnyLocalAddress) ip else bindAddress.canonicalHostName
        val scheme = if (protocol == HTTP) "http" else "https"
        val binding = "$scheme://$hostName:$runtimePort"

        val serverAdapterValue = "$BOLD_ON$CYAN_FG$portName$RESET"

        val hostnameValue = "$BLACK_FG$hostname$RESET"
        val cpuCountValue = "$BLACK_FG$cpuCount$RESET"
        val jvmMemoryValue = "$BLACK_FG$jvmMemory$RESET"

        val javaVersionValue = "$BOLD_ON$BLACK_FG$version$RESET [$BLACK_FG$name$RESET]"

        val localeValue = "$BLACK_FG$locale$RESET"
        val timezoneValue = "$BLACK_FG$timezone$RESET"
        val charsetValue = "$BLACK_FG$charset$RESET"

        val bootTimeValue = "$BOLD_ON$MAGENTA_FG$bootTime s$RESET"
        val usedMemoryValue = "$BOLD_ON$MAGENTA_FG$usedMemory KB$RESET"

        val information = """

            Server Adapter: $serverAdapterValue

            Running in '$hostnameValue' with $cpuCountValue CPUs $jvmMemoryValue KB
            Java $javaVersionValue
            Locale $localeValue Timezone $timezoneValue Charset $charsetValue

            Started in $bootTimeValue using $usedMemoryValue
            Served at $binding${if (protocol == HTTP2) " (HTTP/2)" else ""}
        """.trimIndent()

        val banner = (settings.banner?.let { "$it\n" } ?: banner ) + information
        return banner.indent()
    }
}
