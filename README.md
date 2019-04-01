# RemoteProgrammer

This app provides a drag-and-drop programming environment, mainly meant for education.
It connects to a device hosting an HTTP server, which provides a definition for a programming language.
This server will also receive the programmed functions, and can execute them.

Ideal devices for this are robots equipped with sensors and a raspberry pi, to run the server.
A python server, which offers a connection to the app, and executes the programs as python code, can be found [here](https://github.com/Maximilian-Seitz/RemoteProgrammer_PythonServer).
A microcontroller version of this is in the works, so that booting into an operating system will no longer be nessecary.
