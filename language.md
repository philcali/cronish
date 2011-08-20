## Cron Language 

When describing a cron job, we typically say that it run / executes 
*Every blah at blah on blah*. That's actually describing a pretty
complicated cron job. Most jobs I here about are described thusly...
*Every day at midnight* or simply *At midnight* or *Every half hour*.

We know what that means. I made a first pass at deconstructing a
cron descriptor into parseable (sp?) language a computer can understand.

    every increment [time-connector] [day-connector] [month-connector]

## Incremental Syntax 

*Every day*, *Every other day in every other month*, *Every midnight on the weekend*...
These are incremental statements. Keyword here is *every*.

    increment := month-increment | day-increment | hour-increment | minute-increment
    minute-increment := minute | other minute [minute-from] 
                        | 3-29 minutes [minute-from] | minute-value [minute-from]
    hour-increment := hour | other hour [hour-from] | 3-11 hours [hour-from] 
                        | hour-value [hour-from]
    day-of-week-increment := day | other day [day-from] | day-value [day-from]
    day-of-month-increment := [2,3]1st,[2]2nd,[2]3rd,[1,2]4-0th day of [month-increment] 
                        | other day of [month-increment] | 3-14 days of [month-increment] |
                        | mid-month of [month-increment]
    month-increment := month | other month [month-from] | month-value [month-from]

## From Syntax

*... day from the weekend*,* 

    minute-from := from minute-value
    hour-from := from hour-value
    day-from := from day-value
    month-from := from month-value

## Values

These are the values for each field... in spoken English.

    day-value := Sun-Sat | Sunday-Saturday
    month-value := Jan-Dec | January-December
    hour-value := 0-23
    minute-value := 0-59

## Descriptors

More keywords

    time := hour-value:minute-value
    noon := 12
    midnight := 0
    half-hour := 30
    mid-month := 15
    weekday := Mon-Fri
    weekend := Sat-Sun

## Connector

Connector keywords

    connector := hour-connector | day-connector | month-connector
    hour-connector := at time | at midnight,noon [every minute-increment]
    day-connector := on day-value | on the weekend,weekday
    month-connector := in month-value


Putting it to the test:

    Every day 
    Every other day from the weekend
    Every Wednesday at midnight in July, August, and September
    Every day in September at midnight
    Every day at 1pm-2pm 
