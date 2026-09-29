-- R-2: settlements parked while their rail's circuit was open are re-submitted oldest first, per rail. A partial
-- index keeps that scan proportional to the parked backlog, not to the settlement history.
CREATE INDEX ix_settlement_parked ON settlement.settlement (rail, last_attempt_at)
    WHERE status = 'PENDING' AND last_error_code = 'SETTLEMENT_RAIL_CIRCUIT_OPEN';
