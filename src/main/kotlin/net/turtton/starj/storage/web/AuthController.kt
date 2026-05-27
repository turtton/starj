package net.turtton.starj.storage.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.turtton.starj.security.UserPrincipal
import net.turtton.starj.storage.application.DuplicateUsernameException
import net.turtton.starj.storage.application.InvalidPasswordException
import net.turtton.starj.storage.application.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import java.net.URI
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
class AuthController(
    private val userService: UserService,
    private val authenticationManager: AuthenticationManager,
) {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<RegisterResponse> {
        val response = userService.register(request.username, request.password)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username and password")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<AuthUserResponse> {
        val authToken = UsernamePasswordAuthenticationToken(request.username, request.password)
        val authentication = authenticationManager.authenticate(authToken)

        val existingSession = httpRequest.getSession(false)
        if (existingSession != null) {
            httpRequest.changeSessionId()
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        val principal = authentication.principal as UserPrincipal
        return ResponseEntity.ok(AuthUserResponse(id = principal.id, username = principal.username))
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    fun me(@AuthenticationPrincipal principal: UserPrincipal?): ResponseEntity<AuthUserResponse> {
        if (principal == null) {
            return ResponseEntity.status(401).build()
        }
        return ResponseEntity.ok(AuthUserResponse(id = principal.id, username = principal.username))
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token cookie")
    fun csrf(token: CsrfToken): ResponseEntity<Void> {
        token.token
        return ResponseEntity.ok().build()
    }

    @ExceptionHandler(DuplicateUsernameException::class)
    fun handleDuplicateUsername(ex: DuplicateUsernameException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(ErrorType.CONFLICT.status)
        problem.type = URI.create(ErrorType.CONFLICT.typeUri)
        problem.title = ErrorType.CONFLICT.title
        problem.detail = ex.message
        return ResponseEntity.status(ErrorType.CONFLICT.status).body(problem)
    }

    @ExceptionHandler(InvalidPasswordException::class)
    fun handleInvalidPassword(ex: InvalidPasswordException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(ErrorType.BAD_REQUEST.status)
        problem.type = URI.create(ErrorType.BAD_REQUEST.typeUri)
        problem.title = ErrorType.BAD_REQUEST.title
        problem.detail = ex.message
        return ResponseEntity.status(ErrorType.BAD_REQUEST.status).body(problem)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(ErrorType.UNAUTHORIZED.status)
        problem.type = URI.create(ErrorType.UNAUTHORIZED.typeUri)
        problem.title = ErrorType.UNAUTHORIZED.title
        problem.detail = "Invalid username or password"
        return ResponseEntity.status(ErrorType.UNAUTHORIZED.status).body(problem)
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException::class)
    fun handleAuthenticationException(ex: org.springframework.security.core.AuthenticationException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(ErrorType.UNAUTHORIZED.status)
        problem.type = URI.create(ErrorType.UNAUTHORIZED.typeUri)
        problem.title = ErrorType.UNAUTHORIZED.title
        problem.detail = "Authentication failed"
        return ResponseEntity.status(ErrorType.UNAUTHORIZED.status).body(problem)
    }
}
