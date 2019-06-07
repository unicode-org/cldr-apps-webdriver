#!/bin/bash

HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-standalone-3.141.59.jar -role hub &

for ((PORT = 5555; PORT <= 5564; PORT++))
do  
	HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-standalone-3.141.59.jar -role node -port $PORT &
done
