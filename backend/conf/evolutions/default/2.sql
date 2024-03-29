-- Add Databases

-- !Ups

create table databases
(
    id         UUID PRIMARY KEY,
    user_id    UUID not null references users (id),
    name       varchar   not null,
    created_at timestamp not null,
    port       integer
);

-- !Downs

drop table "databases" if exists;