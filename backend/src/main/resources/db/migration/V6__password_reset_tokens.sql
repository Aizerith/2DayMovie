create table password_reset_token (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    token varchar(255) not null unique,
    expires_at timestamp not null,
    used_at timestamp null,
    created_at timestamp not null default current_timestamp
);

create index idx_password_reset_token_user_id on password_reset_token(user_id);
