alter table github_teams add column slug varchar(255);
update github_teams set slug = 'eap-push' where id = 3;
update github_teams set slug = 'eap-view' where id = 1;
update github_teams set slug = 'protean-view' where id = 2;
