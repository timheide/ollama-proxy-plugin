package de.timheide.ollamaproxyplugin.ui

import de.timheide.ollamaproxyplugin.llm.AnthropicProvider
import de.timheide.ollamaproxyplugin.services.OllamaServerService
import de.timheide.ollamaproxyplugin.settings.OllamaSettingsState
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities


class OllamaToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = OllamaToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)

        // Add settings action to gear menu
        val settingsAction = object : AnAction("Settings...", "Open Ollama Proxy Settings", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    "Ollama Proxy Settings"
                )
            }
        }

        toolWindow.setTitleActions(listOf(settingsAction))
    }
}




class OllamaToolWindowContent(private val project: Project) {
    private val logger = LoggerFactory.getLogger(OllamaToolWindowContent::class.java)
    private val serverService = project.getService(OllamaServerService::class.java)
    private var isServerRunning = false
    private var isShuttingDown = false
    private val statusLabel = JBLabel()
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop Server").apply { isEnabled = false }
    private val loadingIcon = AnimatedIcon.Default()
    private val loadingLabel = JLabel(loadingIcon)

    init {
        updateUIState()
    }

    fun getContent(): JPanel {
        val panel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(10)
        }

        // Status panel with icon
        val statusPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(10)
            add(JBLabel(AllIcons.General.Information), BorderLayout.WEST)
            add(statusLabel.apply {
                border = JBUI.Borders.emptyLeft(5)
            }, BorderLayout.CENTER)
        }

        // Button panel with proper styling
        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 5, 0)).apply {
            add(startButton)
            add(stopButton)
        }

        // Add button listeners
        startButton.addActionListener {
            startServer()
        }

        stopButton.addActionListener {
            stopServer()
        }

        // Main content panel
        val contentPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(statusPanel, BorderLayout.NORTH)
            add(buttonPanel, BorderLayout.CENTER)
            add(loadingLabel.apply { isVisible = false }, BorderLayout.EAST)
        }

        panel.add(contentPanel, BorderLayout.NORTH)

        return panel
    }



    private fun startServer() {
        try {
            val apiKey = OllamaSettingsState.instance.apiKey
            if (apiKey.isBlank()) {
                throw IllegalStateException("API key is required. Please set it in Settings -> Tools -> Ollama Proxy Settings.")
            }

            val provider = AnthropicProvider(apiKey)
            serverService.startServer(provider)
            isServerRunning = true
            updateUIState()
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Ollama Proxy")
                .createNotification(
                    "Failed to start server",
                    e.message ?: "Unknown error",
                    NotificationType.ERROR
                ).notify(project)
        }
    }

    private fun stopServer() {
        isShuttingDown = true
        updateUIState()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                serverService.stopServer()
                SwingUtilities.invokeLater {
                    isServerRunning = false
                    isShuttingDown = false
                    updateUIState()
                }
            } catch (e: Exception) {
                logger.error("Error stopping server", e)
                SwingUtilities.invokeLater {
                    isShuttingDown = false
                    updateUIState()
                }
            }
        }
    }

    private fun updateUIState() {
        val port = OllamaSettingsState.instance.port
        when {
            isShuttingDown -> {
                statusLabel.text = "Server Status: Shutting down... (Port: $port)"
                loadingLabel.isVisible = true
                startButton.isEnabled = false
                stopButton.isEnabled = false
            }
            isServerRunning -> {
                statusLabel.text = "Server Status: Running on port $port"
                loadingLabel.isVisible = false
                startButton.isEnabled = false
                stopButton.isEnabled = true
            }
            else -> {
                statusLabel.text = "Server Status: Stopped"
                loadingLabel.isVisible = false
                startButton.isEnabled = true
                stopButton.isEnabled = false
            }
        }
    }
}
