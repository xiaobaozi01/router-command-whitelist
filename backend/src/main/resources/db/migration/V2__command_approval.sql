CREATE TABLE command_approval_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    target_command_id BIGINT NULL,
    target_command_version BIGINT NULL,
    before_snapshot TEXT NULL,
    proposed_snapshot TEXT NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    submitter_user_id BIGINT NULL,
    submitter_username VARCHAR(64) NOT NULL,
    submitter_display_name VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewer_username VARCHAR(64) NULL,
    reviewer_display_name VARCHAR(100) NULL,
    reviewed_at TIMESTAMP NULL,
    review_comment VARCHAR(500) NULL,
    generated_command_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_command_approval_status_time
    ON command_approval_request(status, submitted_at);
CREATE INDEX idx_command_approval_submitter_time
    ON command_approval_request(submitter_username, submitted_at);
CREATE INDEX idx_command_approval_target_status
    ON command_approval_request(target_command_id, status);
