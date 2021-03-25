
create sequence sq_unsubscribed_users_from_teams;

create table unsubscribed_users_from_teams (
    id bigint default nextval('sq_unsubscribed_users_from_teams') primary key,
    user_removal_id bigint not null,
    github_username varchar(255),
    github_team_name varchar(255),
    github_org_name varchar(255),
    status varchar(255),
    created timestamp default CURRENT_TIMESTAMP,
    constraint fk_unsubscribed_users_from_teams foreign key (user_removal_id) references user_removals (id)
);

create sequence sq_unsubscribed_users_from_orgs;

create table unsubscribed_users_from_orgs (
    id bigint default nextval('sq_unsubscribed_users_from_orgs') primary key,
    user_removal_id bigint not null,
    github_username varchar(255),
    github_org_name varchar(255),
    status varchar(255),
    created timestamp default CURRENT_TIMESTAMP,
    constraint fk_unsubscribed_users_from_orgs foreign key (user_removal_id) references user_removals (id)
);