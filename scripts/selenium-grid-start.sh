#!/bin/bash

HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-4.16.1.jar standalone &

for ((PORT = 5555; PORT <= 5563; PORT++))
do  
	HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-4.16.1.jar standalone --port $PORT &
done
