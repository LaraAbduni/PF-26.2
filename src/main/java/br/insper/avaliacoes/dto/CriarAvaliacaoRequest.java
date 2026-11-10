package br.insper.avaliacoes.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CriarAvaliacaoRequest(
    @NotBlank(message = "Informe o e-mail da pessoa avaliada.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 254, message = "Use até 254 caracteres.") String emailAvaliado,
    @NotBlank(message = "Escreva um comentário.")
    @Size(max = 2000, message = "Use até 2000 caracteres.") String comentarios,
    @NotNull(message = "Informe a nota.")
    @DecimalMin(value = "0.0", message = "A nota deve ser de 0 a 10.")
    @DecimalMax(value = "10.0", message = "A nota deve ser de 0 a 10.")
    @Digits(integer = 2, fraction = 1, message = "Use no máximo uma casa decimal.") BigDecimal nota
) {}
