insert into github_orgs values (1, 'testorg');

insert into github_teams values (1, 1, 'Team 1', 1);
insert into github_teams values (2, 1, 'Team 2', 2);
insert into github_teams values (3, 1, 'Team 3', 3);

insert into application_parameters (param_name, param_value) values ('github.token', '');
insert into application_parameters (param_name, param_value) values ('application.reporting_email', 'info@example.com');
insert into application_parameters (param_name, param_value) values ('application.unsubscribe_users', 'false');
insert into application_parameters (param_name, param_value) values ('application.archive_root', '/tmp/mjolnir-repository-archive');
insert into application_parameters (param_name, param_value) values ('ldap.url', 'ldap://ldap.example.com');
insert into application_parameters (param_name, param_value) values ('ldap.search_context', 'ou=users,dc=example,dc=com');

insert into users (krb_name, github_name) values ('thofman', 'TomasHofman');
insert into APPLICATION_PARAMETERS (PARAM_NAME,PARAM_VALUE) values ('application.remove_archives', 'false');
insert into APPLICATION_PARAMETERS (PARAM_NAME,PARAM_VALUE) values ('application.remove_archives_after', '90');
