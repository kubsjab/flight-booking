-- TABLES

DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);

DROP TABLE IF EXISTS snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);



-- FUNCTIONS

CREATE OR REPLACE FUNCTION  bytea_to_json(msg bytea) RETURNS json AS $$
DECLARE
    encoded_msg text := encode(msg, 'escape');
BEGIN
    RETURN (substring(
            substring(encoded_msg for length(encoded_msg) - position('}' in reverse(encoded_msg)) + 1)
            from position('{' in encoded_msg)))::json;
END;$$
    LANGUAGE PLPGSQL;


CREATE OR REPLACE FUNCTION  bytea_to_jsonb(msg bytea) RETURNS jsonb AS $$
DECLARE
    encoded_msg text := encode(msg, 'escape');
BEGIN
    RETURN (substring(
            substring(encoded_msg for length(encoded_msg) - position('}' in reverse(encoded_msg)) + 1)
            from position('{' in encoded_msg)))::jsonb;
END;$$
    LANGUAGE PLPGSQL;