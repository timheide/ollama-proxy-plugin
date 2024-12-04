package de.timheide.ollamaproxyplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import de.timheide.ollamaproxyplugin.Server
import de.timheide.ollamaproxyplugin.provider.LLMProvider
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.Socket
import java.io.IOException
@Service(Level.PROJECT)
class OllamaServerService(private val project: Project) {
    private var server: Server? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = LoggerFactory.getLogger(OllamaServerService::class.java)

    fun startServer(provider: LLMProvider) {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            server = Server(provider)
            server?.start()
        }
    }

    fun stopServer() {
        runBlocking {
            try {
                logger.info("Stopping server...")
                server?.stop()
                serverJob?.cancelAndJoin()
                server = null
                serverJob = null

                delay(1000) // Give some time for port to be released

                if (isPortInUse(8080)) {
                    logger.error("Port 8080 still in use after server stop")
                } else {
                    logger.info("Server port successfully released")
                }
            } catch (e: Exception) {
                logger.error("Error stopping server", e)
            }
        }
    }


    private fun isPortInUse(port: Int): Boolean {
        return try {
            Socket("localhost", port).use { true }
        } catch (e: IOException) {
            false
        }
    }

    fun isRunning(): Boolean = serverJob?.isActive == true
}
