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


-- MATERIALIZED VIEWS

-- managers_view

-- Contains information about creating clients, airlines and brokers.
-- To refresh run:
--     REFRESH MATERIALIZED VIEW CONCURRENTLY managers_view;

DROP MATERIALIZED VIEW IF EXISTS managers_view;

CREATE MATERIALIZED VIEW managers_view
AS
WITH events AS (
    SELECT ordering,
           persistence_id,
           sequence_number,
           deleted,
           tags,
           bytea_to_jsonb(journal.message) as payload
    FROM journal
    WHERE persistence_id LIKE 'manager-%'
)
SELECT
    ordering,
    persistence_id,
    sequence_number,
    tags,
    payload
FROM events
WITH DATA;


-- bookings_view

-- Contains information about bookings. It contains data for all flights and all flight-booking events like
-- ticket booked, booking cancelled or overbooked.
-- To refresh run:
--     REFRESH MATERIALIZED VIEW CONCURRENTLY bookings_view;

DROP MATERIALIZED VIEW IF EXISTS bookings_view;

CREATE MATERIALIZED VIEW bookings_view
AS
WITH events AS (
    SELECT ordering,
           persistence_id,
           sequence_number,
           deleted,
           tags,
           bytea_to_jsonb(journal.message) as payload
    FROM journal
    WHERE persistence_id LIKE 'flight-%'
)
SELECT
    ordering,
    persistence_id,
    sequence_number,
    tags,
    payload ->> 'seatId' AS seat,
    payload -> 'booking'->> 'bookingId' AS bookingId,
    CONCAT (payload -> 'booking' -> 'customer'->> 'firstName', ' ', payload -> 'booking' -> 'customer'->> 'lastName') AS customer,
    payload -> 'booking'->> 'createdDate' AS datetime,
    payload
FROM events
WITH DATA;


-- clients_view

-- Contains information about clients and their requests.
-- To refresh run:
--     REFRESH MATERIALIZED VIEW CONCURRENTLY clients_view;

DROP MATERIALIZED VIEW IF EXISTS clients_view;

CREATE MATERIALIZED VIEW clients_view
AS
WITH events AS (
    SELECT ordering,
           persistence_id,
           sequence_number,
           deleted,
           tags,
           bytea_to_jsonb(journal.message) as payload
    FROM journal
    WHERE persistence_id LIKE 'client-%'
)
SELECT
    ordering,
    persistence_id,
    sequence_number,
    tags,
    payload
FROM events
WITH DATA;

