insert into github_orgs values (1, 'testorg', true, true);

insert into github_teams values (1, 1, 'Team 1', 1, false);
insert into github_teams values (2, 1, 'Team 2', 2, false);
insert into github_teams values (3, 1, 'Team 3', 3, false);

insert into application_parameters (param_name, param_value) values ('github.token', '');
insert into application_parameters (param_name, param_value) values ('application.sender_email', 'from@example.com');
insert into application_parameters (param_name, param_value) values ('application.reporting_email', 'to@example.com');
insert into application_parameters (param_name, param_value) values ('application.unsubscribe_users', 'false');
insert into application_parameters (param_name, param_value) values ('application.archive_root', '/tmp/mjolnir-repository-archive');
insert into application_parameters (param_name, param_value) values ('ldap.url', 'ldap://ldap.example.com');
insert into application_parameters (param_name, param_value) values ('ldap.search_context', 'ou=users,dc=example,dc=com');
insert into application_parameters (param_name, param_value) values ('application.remove_archives', 'false');
insert into application_parameters (param_name, param_value) values ('application.remove_archives_after', '90');

insert into users (krb_name, github_name, github_id) values ('thofman', 'TomasHofman', 123);
