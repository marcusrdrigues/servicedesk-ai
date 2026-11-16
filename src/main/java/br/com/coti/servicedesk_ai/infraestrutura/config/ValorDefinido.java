package br.com.coti.servicedesk_ai.infraestrutura.config;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejeita valor nulo, em branco ou um ${VARIAVEL} que não foi resolvido. O Spring Boot não falha em
 * placeholder sem valor ao fazer o binding: deixa o texto literal, e um segredo literal é adivinhável.
 * A mensagem cita só o nome da propriedade (pelo caminho da violação), nunca o valor.
 */
@Documented
@Constraint(validatedBy = ValorDefinido.Validador.class)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValorDefinido {

    String message() default "deve estar definido (variável de ambiente ausente, em branco ou sem resolver)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validador implements ConstraintValidator<ValorDefinido, String> {

        public static boolean definido(String valor) {
            return valor != null && !valor.isBlank() && !valor.contains("${");
        }

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return definido(valor);
        }
    }
}
