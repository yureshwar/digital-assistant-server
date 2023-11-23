ALTER TABLE `udan`.`SequenceList` ADD COLUMN `additionalParams` JSON DEFAULT NULL after `isIgnored`;
ALTER TABLE `udan`.`SequenceList`
	CHANGE COLUMN `deleted` `deleted` INT(1) NOT NULL DEFAULT 0 ,
	CHANGE COLUMN `isValid` `isValid` INT(1) NOT NULL DEFAULT 1 ,
	CHANGE COLUMN `isIgnored` `isIgnored` INT(1) NOT NULL DEFAULT 0;
ALTER TABLE `udan`.`sequencevotes`
	CHANGE COLUMN `upvote` `upvote` INT(1) NOT NULL ,
	CHANGE COLUMN `downvote` `downvote` INT(1) NOT NULL ;
ALTER TABLE `udan`.`Userclicknodes`
	CHANGE COLUMN `clickednodename` `clickednodename` VARCHAR(2000) NULL DEFAULT NULL ;


