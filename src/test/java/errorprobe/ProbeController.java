package errorprobe;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A test-only controller that raises each kind of failure, for GlobalExceptionHandlerTest. It lives
 * outside the com.campuscompanion package on purpose, so the application's component scan never
 * registers it; the test adds it by hand.
 */
@RestController
@RequestMapping("/probe")
public class ProbeController {

  public static final String INTERNAL_MESSAGE = "select * from app.users where password_hash = 'x'";

  public record ProbeBody(@NotBlank String name, @Size(max = 5) String code) {}

  public record ProbePage(@Max(100) int size) {}

  @GetMapping("/api/{code}")
  public String api(@PathVariable ErrorCode code) {
    throw new ApiException(code, "Shown to the user.");
  }

  @GetMapping("/not-found")
  public String notFound() {
    throw new ResourceNotFoundException();
  }

  @GetMapping("/conflict")
  public String conflict() {
    throw new ConflictException("A student with this roll number already exists.");
  }

  @PostMapping("/body")
  public String body(@Valid @RequestBody ProbeBody body) {
    return "ok";
  }

  @GetMapping("/constraint")
  public String constraint() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      throw new ConstraintViolationException(factory.getValidator().validate(new ProbePage(500)));
    }
  }

  @GetMapping("/number")
  public String number(@RequestParam int value) {
    return "ok";
  }

  @GetMapping("/unexpected")
  public String unexpected() {
    throw new IllegalStateException(INTERNAL_MESSAGE);
  }
}
