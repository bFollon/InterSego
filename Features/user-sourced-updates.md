# Feature description
I want to implement a sort of user-sourced "live-updates" feature.

## Problem
Right now, all the departure times are static and estimated. Historically, Linecar buses are not very punctual and can either depart early, depart late or even there have been cases of busses straight up not departing, leaving users stranded with no warning whatsoever.

This leads to a horrible user experience in which you never know if youa rrived slightly too late and the bus passed early, or if it's going to be 20 minutes late and you should stay and wait. With busses in many cases departing every hour, this can be a very bad experience.

## Target solution
The ideal solution would be for drivers to have a way to report their location. But this app is not being developed with the cooperation of the bus company help and is a standalone effort.
The next best thing would be for the app to report the user's location intelligently and only during the trip so other users could have a live update of the bus.

## MVP
As an MVP, the solution will simply be allowing users to notify whenever they get on the bus.  
This would solve two issues:
- Clear up the uncertainty of "Will this departure even be served?" to downstream users.
- Provide a more accurate ETA to users downstream.

To implement this, I have created a `server` folder in which we will implement a simple server that will have two endpoints (as a starting point):
- One endpoint to receive boarding notifications from users
  - That endpoint will need to receive:
    - Boarded stop
    - Time of boarding
- One endpoint to query current active notifications that can help the app provide better ETA and inform users that the bus that will serve their departure has indeed departed from a stop prior to theirs

The stop ID to send to the boarding endpoint should be derived from the user's location  
Either the server or the app should be able to, based on the bus stop and the time, determine which ETA they can provide an updated ETA for.

Busses serve lines on a trip basis, event if the route is circular.  
If a user notifies a boarding, updated ETA should only apply to one trip / one cicle of the circular route.

## Server stack
The server should be implemented in nodejs, with some framework aimed at services, like next.js or something similar.
Ideally, we should use typescript to have some type safety.

We should use some file-based database. Data structures should be JSON or some typescript-compatible technology.
