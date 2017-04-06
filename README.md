CSCI 342 Fall 2015

Chatroom.java is a server program that manages a chatroom. It operates as follows:

Start the program on the command line: java Chatroom <port number>

The program takes in an optional commandline argument, specifying the port you would like the Chatroom server to listen on. The default port number is 8080.

A client can enter the Chatroom via telnet <IP address> <port number>
To exit Chatroom, use the escape character ^] (ctrl + ]), enter, then type close. 

This Chatroom uses multithreading to manage the client conversations.

