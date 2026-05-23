-- Run if existing DB still has placeholder hashes from the original seed file.
-- Password after update: 123456

UPDATE users SET password = '$2a$10$uuDx3I721W9gWiUIq0gx6.trwrSkh/zsHLxDQuFJTdM/XbKfti2sm' WHERE username IN ('vincent', 'admin', 'tester');
