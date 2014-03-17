#!/bin/sh
rm -f work/receive/Topic/* work/receive/TopicA/* work/receive/.TopicA/*
mv work/publish/.camel/TopicA_ExactlyOnce_true work/publish/TopicA_ExactlyOnce_true
mv work/publish/Topic/.camel/C_atMostOnce_true work/publish/Topic/C_atMostOnce_true
mv work/publish/TopicA/.camel/B_atLeastOnce_false work/publish/TopicA/B_atLeastOnce_false
mv work/publish/emptyLevel/.camel/TopicA_atMostOnce_false work/publish/emptyLevel/TopicA_atMostOnce_false
sleep 5
for dir in work/receive/Topic work/receive/TopicA work/receive/.TopicA/*
do
	set nFiles = `ls -1 $dir | wc -l`
	if [ "$nFiles" == "0" ] ; then echo "No message received in "$dir; fi
done
