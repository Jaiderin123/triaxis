-- Applies one triage event to the urgency view atomically (ADR 0021).
-- KEYS[1] patient hash (state, level)   KEYS[2] counters hash (level -> count)   KEYS[3] meta hash
-- ARGV[1] kind: PRESENT or DISCHARGED   ARGV[2] level of the event   ARGV[3] event time (epoch milliseconds)
-- ARGV[4] time to keep a discharged patient, in seconds
-- Returns 1 when a count changed, 0 otherwise.
local state = redis.call('HGET', KEYS[1], 'state')
local changed = 0
if ARGV[1] == 'DISCHARGED' then
  if not state then
    -- Discharge before any other event: remember it, so a late event never counts the patient.
    redis.call('HSET', KEYS[1], 'state', 'discharged', 'level', ARGV[2])
    redis.call('EXPIRE', KEYS[1], ARGV[4])
  elseif state == 'active' then
    redis.call('HINCRBY', KEYS[2], redis.call('HGET', KEYS[1], 'level'), -1)
    redis.call('HSET', KEYS[1], 'state', 'discharged')
    redis.call('EXPIRE', KEYS[1], ARGV[4])
    changed = 1
  end
elseif not state then
  -- First event of a patient that is not discharged: it is active at the level of the event.
  redis.call('HSET', KEYS[1], 'state', 'active', 'level', ARGV[2])
  redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
  changed = 1
end
local newest = tonumber(redis.call('HGET', KEYS[3], 'lastEventAt') or '0')
if tonumber(ARGV[3]) > newest then
  redis.call('HSET', KEYS[3], 'lastEventAt', ARGV[3])
end
return changed
