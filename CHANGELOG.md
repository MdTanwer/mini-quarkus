# Changelog

All notable changes to mini-quarkus will be documented in this file.

## [Unreleased]
### Added
- Initial implementation of minimal Quarkus-inspired framework
- ArC container with basic CDI-like dependency injection
- RESTEasy Reactive server with GET route support
- Bootstrap system with application model loading
- Annotation processing for build-time bean discovery
- Integration tests demonstrating end-to-end functionality
- Maven multi-module project structure with BOMs

### Features
- `@Singleton` and `@Inject` annotations for dependency injection
- Build-time bean registration and wiring
- Vert.x-based HTTP server
- Generated application model for fast startup
- ServiceLoader-based extension discovery

## [0.1.0] - 2026-04-13
### Initial Release
- Core framework architecture
- Basic dependency injection container
- Minimal HTTP server implementation
- Build-time processing foundation
- Sample application and integration tests
