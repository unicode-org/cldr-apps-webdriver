-- Add Survey Tool users to the database, needed for running SurveyDriver. It must be consistent with SurveyDriver.getNodeLoginQuery.
-- Usage: mysql cldrdb < scripts/cldr-add-webdrivers.sql
insert into cldr_users(userlevel, name, email, org, password) values(1, 'SundayDriver_TESTER_', 'sundaydriver.ta9emn2f.@czca.bangladesh.example.com', 'Bangladesh Computer Council', 'ME0BtTx7J');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'MondayDriver_TESTER_', 'mondaydriver.fvuisg2in@sisi.sil.example.com', 'SIL', 'OjATx0fTt');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'TuesdayDriver_TESTER_', 'tuesdaydriver.smw4grsg0@ork0.netflix.example.com', 'Netflix', 'QEuNcNCvi');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'WednesdayDriver_TESTER_', 'wednesdaydriver.kesjczv8q@8sye.afghan-csa.example.com', 'Afghan_csa', 'MjpHbYuJY');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'ThursdayDriver_TESTER_', 'thursdaydriver.klxizrpyc@p9mn.welsh-lc.example.com', 'Welsh LC', 'cMkLuCab1');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'FridayDriver_TESTER_', 'fridaydriver.kclabyoxi@fgkg.mozilla.example.com', 'Mozilla', 'qSR.KZ57V');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'SaturdayDriver_TESTER_', 'saturdaydriver.oelbvfn0x@smiz.cherokee.example.com', 'Cherokee', 'r3Lim3OFL');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'BackseatDriver_TESTER_', 'backseatdriver.cogihy42h@jqs9.india.example.com', 'India', 'LenA3VJSK');
insert into cldr_users(userlevel, name, email, org, password) values(1, 'StudentDriver_TESTER_', 'studentdriver.h.ze76.2p@nd3e.government of pakistan - national language authority.example.com', 'Government of Pakistan - National Language Authority', 'S5fpuRqHW');
