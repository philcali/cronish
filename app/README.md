# Cronish App

The cronish app can execute shell commands as a daemon.

## Installation

    cs philcali/cronish

That's it!

## Usage (Daemon)

The cronish program's main function is a interval daemon very much
like crontab is for linux machines. 

To start the daemon, simply run:

    > cronish -d

To background the program:
    
    > cronish -d > /dev/null &

This will silent the interval execution like a standard crontab

Use the `-t` to isolate this cronish run. This means that for the duration of
the run, cronish will run and forget it's tasks upon exit

## Usage (Client)

If you are interested in interpreting English into cronish, run:

    > cronish <cronish>

ie:

    > cronish every 5 seconds
    */5 * * * * * *

    > cronish every midnight on the last Friday in July in the year 2012
    0 0 0 * 7 5L 2012

To add something to the *cronishtab*, the syntax is simple:

    > cronish <command> runs <cronish>
    > cronish wget http://google.com runs every minute

To list the running tasks in *cronishtab*:

    > cronish -l
    {0}: wget http://google.com runs every minute

To stop a currently executing task, and remove it for future reference:
    
    > cronish -r 0

