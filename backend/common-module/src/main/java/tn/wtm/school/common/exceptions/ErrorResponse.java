package tn.wtm.school.common.exceptions;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Builder
public class ErrorResponse {

    private String message;
    private String error;
    private Integer status;
    private LocalDateTime timestamp;
    private Set<String> violations;

}
