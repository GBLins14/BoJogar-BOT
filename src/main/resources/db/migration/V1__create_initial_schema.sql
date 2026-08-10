CREATE TABLE organizadores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE peladas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(10) NOT NULL UNIQUE,
    organizador_id UUID NOT NULL REFERENCES organizadores(id),
    esporte VARCHAR(20) NOT NULL,
    descricao TEXT,
    data_hora TIMESTAMP NOT NULL,
    local VARCHAR(255) NOT NULL,
    limite_jogadores INT NOT NULL,
    valor_por_jogador NUMERIC(10, 2) NOT NULL DEFAULT 0,
    chave_pix VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE jogadores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(20) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE inscricoes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pelada_id UUID NOT NULL REFERENCES peladas(id),
    jogador_id UUID NOT NULL REFERENCES jogadores(id),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO',
    posicao_lista_espera INT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (pelada_id, jogador_id)
);

CREATE TABLE pagamentos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inscricao_id UUID NOT NULL REFERENCES inscricoes(id),
    valor NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    transaction_id VARCHAR(255),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_peladas_organizador_id ON peladas(organizador_id);
CREATE INDEX idx_peladas_status ON peladas(status);
CREATE INDEX idx_inscricoes_pelada_id ON inscricoes(pelada_id);
CREATE INDEX idx_inscricoes_jogador_id ON inscricoes(jogador_id);
CREATE INDEX idx_pagamentos_inscricao_id ON pagamentos(inscricao_id);
