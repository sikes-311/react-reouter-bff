package com.mycompany.app.shared.exception

class UnauthorizedException(message: String = "Unauthorized") : RuntimeException(message)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class DownstreamClientException(message: String) : RuntimeException(message)

class DownstreamServerException(message: String) : RuntimeException(message)
