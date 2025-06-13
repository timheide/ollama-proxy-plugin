package de.timheide.ollamaproxyplugin.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object ClaudeErrorHandler {
    fun handleError(project: Project?, message: String, details: String? = null) {
        val content = if (details != null) "$message: $details" else message
        
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ollama Proxy Plugin")
            .createNotification("Claude API Error", content, NotificationType.ERROR)
            .notify(project)
    }
    
    fun handleGenericError(title: String, message: String, project: Project?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ollama Proxy Plugin")
            .createNotification(title, message, NotificationType.ERROR)
            .notify(project)
    }
    
    fun handleClaudeError(operation: String, exception: Throwable, project: Project?) {
        val message = "Failed to $operation: ${exception.message ?: exception.javaClass.simpleName}"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Ollama Proxy Plugin")
            .createNotification("Claude API Error", message, NotificationType.ERROR)
            .notify(project)
    }
}