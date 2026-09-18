ALTER TABLE user_activities
    ADD UNIQUE KEY uq_ua (user_id, expo_id, event_type);
