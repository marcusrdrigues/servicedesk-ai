package br.com.coti.servicedesk_ai.dominio;

public record AnaliseChamado(
        Categoria categoria, Prioridade prioridade, String resumo, String orientacao, boolean enviarEmail) {}
