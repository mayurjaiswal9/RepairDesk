-- Customer's written response when admin requests more information (TRACK-002 enhancement)
ALTER TABLE repair_requests
    ADD COLUMN customer_info_response TEXT;
