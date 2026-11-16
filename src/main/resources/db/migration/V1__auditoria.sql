-- Auditoria mínima (spec auditoria-execucoes). Não guarda nome, descrição, resumo nem corpo de e-mail.
-- status_ordem existe para a atualização condicional: um estado só é gravado se a ordem avançar.

CREATE TABLE execucao (
    id            UUID         PRIMARY KEY,
    criado_em     TIMESTAMPTZ  NOT NULL,
    provedor      VARCHAR(30)  NOT NULL,
    categoria     VARCHAR(20)  NOT NULL,
    prioridade    VARCHAR(20)  NOT NULL,
    status        VARCHAR(40)  NOT NULL,
    status_ordem  SMALLINT     NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL
);

CREATE TABLE notificacao (
    evento_id     UUID         PRIMARY KEY,
    execucao_id   UUID         NOT NULL REFERENCES execucao (id),
    status        VARCHAR(40)  NOT NULL,
    status_ordem  SMALLINT     NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    ultimo_erro   VARCHAR(200)
);

CREATE INDEX notificacao_execucao_idx ON notificacao (execucao_id);
