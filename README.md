# Ollama Proxy Plugin for JetBrains IDE

A JetBrains IDE plugin that enables using Anthropic's latest Claude models including Claude 4 Opus, Claude 4 Sonnet, Claude 3.7 Sonnet, and Claude 3.5 models with the JetBrains AI Assistant. It works by providing a proxy that mimics the Ollama API format, allowing you to use Claude models through the AI Assistant's "Enable Ollama" feature in the Third-party AI providers settings.

## Features

- **Real-time Streaming**: Incremental message streaming for responsive chat experience with JetBrains AI Assistant
- **Latest Claude Models**: Support for Claude 4 Opus, Claude 4 Sonnet, Claude 3.7 Sonnet, and Claude 3.5 models
- **Robust error handling** for interrupted or malformed streaming responses

![Plugin Demo](./assets/demo.gif)

## Installation

1. Download the plugin from JetBrains Marketplace (or build from source)
2. Install it in JetBrains IDE: Settings → Plugins → Install Plugin from Disk
3. Restart JetBrains IDE

## Configuration

1. Go to Settings → Tools → Ollama Proxy Settings
2. Enter your Anthropic API key (Get one from [Anthropic Console](https://console.anthropic.com/settings/keys))
3. Optionally configure the proxy port (default: 11434)

## Usage

1. Open the Ollama Proxy tool window (View → Tool Windows → Ollama Proxy)
2. Click "Start Server" to begin the proxy service
3. The proxy will be available at http://localhost:11434
4. In the JetBrains AI Assistant Settings enable the Ollama integration via "Third-party AI providers".
5. You can now use the latest Claude models including Claude 4 Opus, Claude 4 Sonnet, and Claude 3.7 Sonnet in the model dropdown in the AI Assistant
6. If the models do not show up please disable Ollama in the JetBrains AI Assistant and enable it again.

## API Endpoints

- GET /api/tags - List available models
- POST /api/chat - Chat completion endpoint
- POST /api/show - Get model details

## Building from Source

### Prerequisites
- Java 17 or later
- Gradle 8.0 or later

### Build Commands

```bash
# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Clean and build
./gradlew clean buildPlugin
```

The plugin ZIP file will be created in `build/distributions/ollama-proxy-plugin-0.1.0.zip`.

### Testing

The project includes comprehensive tests for:
- **Real-time Streaming**: Tests for incremental message streaming and real-time response handling
- **Streaming Response Handling**: Tests for robust JSON parsing and error recovery in streaming responses from Anthropic API
- **API Integration**: Tests for authentication, error handling, and model management  
- **JetBrains AI Assistant Compatibility**: Verification that streaming responses work correctly with the IDE integration
- **New Model Support**: Tests for Claude 4 Opus, Claude 4 Sonnet, and Claude 3.7 Sonnet integration

Run specific test suites:
```bash
# Run all tests
./gradlew test

# Run streaming-specific tests
./gradlew test --tests="*Streaming*"

# Run simple functionality tests
./gradlew test --tests="*Simple*"
```

### Recent Improvements

**v0.1.0** - Production-ready release with comprehensive streaming and UI improvements:
- **New Claude Models**: Added support for Claude 4 Opus and Claude 4 Sonnet (latest flagship models)
- **Added Claude 3.7 Sonnet**: Extended thinking capabilities model now available
- **Complete Tool Window Integration**: Dedicated Ollama Proxy tool window with start/stop controls and status monitoring
- **Real-time Message Streaming**: True end-to-end streaming from Anthropic API to JetBrains AI Assistant
- **Fixed JetBrains AI Assistant Compatibility**: Resolved streaming response format issues and deserialization errors
- **Fixed "Unexpected EOF" Streaming Errors**: Improved streaming response handling using proper Ktor streaming mechanisms
- **Fixed Write Timeout Exceptions**: Extended server write timeout to 10 minutes for long streaming responses
- **Comprehensive Test Suite**: Fixed all failing tests - now 100% test success rate (37/37 tests passing)
- **Enhanced Error Handling**: Improved streaming error recovery and JSON validation
- **Production-Ready Stability**: Robust build system and deployment-ready package
- Improved JSON parsing for streaming responses from Anthropic API
- Better error recovery for interrupted or malformed JSON chunks
- Added comprehensive test suite for streaming functionality
- Enhanced error handling to prevent crashes during streaming

## Requirements

- JetBrains IDE 2024.1 or later (updated from 2023.1)
- Java 17 or later
- Valid Anthropic API key

## Troubleshooting

### Streaming Issues
If you experience issues with streaming responses (messages cutting off, JSON parsing errors):

1. **Check the plugin logs** in IDE Help → Show Log in Files
2. **Restart the proxy server** using the tool window
3. **Verify API key** is correctly configured in Settings
4. **Check network connectivity** to Anthropic API

The plugin includes enhanced error recovery for streaming issues as of v0.1.0.

### Model Not Showing Up
If Claude models don't appear in JetBrains AI Assistant:

1. Ensure the proxy server is running (green status in tool window)
2. In AI Assistant settings, **disable** Ollama integration
3. **Re-enable** Ollama integration 
4. Refresh the model list

### Common Error Messages
- `Failed to process streaming response line`: Usually harmless, indicates improved error recovery is working
- `unexpected EOF`: Fixed in v0.1.0 - update to latest version if you see this error
- `WriteTimeoutException`: Fixed in v0.1.0 with extended 10-minute write timeout for long responses
- `Authentication failed`: Check your Anthropic API key
- `Rate limit exceeded`: Wait a few minutes before making new requests

## License

This project is licensed under the MIT License.
