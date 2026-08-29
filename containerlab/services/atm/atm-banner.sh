#!/bin/sh
# Printed to each TCP client on 9090. Invoked by BusyBox: nc -lk -e
printf 'ATM-01 service OK\n'
