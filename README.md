# CAB401Assignment
QUT CAB401 (High Performance &amp; Parallel Computing) assignment.

# Building

To build this project, simple checkout the git repository locally and run:

`./gradlew build`

This uses the bundled Gradle wrapper to build the application. Output files will be placed in `build/libs/`

# Running

To run the application take the created `.jar` file, and place it in a directory alongside `jacobi.jar` with `referenceGenes.list` and `Ecoli/` in 
the parent directory.

Once this is done, run,

`java -cp "jacobi.jar:CAB401Assignment-1.0-SNAPSHOT.jar" qut.Tester`

in a terminal.

## Running only the Parallel Application

To run only the parallel application, use the following command instead,

`java -cp "jacobi.jar:CAB401Assignment-1.0-SNAPSHOT.jar" qut.Parallel`

## Running only the Sequential Application

To run only the sequential application, use the following command instead,

`java -cp "jacobi.jar:CAB401Assignment-1.0-SNAPSHOT.jar" qut.Sequential`
