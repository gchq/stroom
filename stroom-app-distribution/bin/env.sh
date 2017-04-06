ulimit -v unlimited
 
export MALLOC_ARENA_MAX=4

export JAVA_OPTS="${JAVA_OPTS} -Djava.awt.headless=true @@JAVA_OPTS@@"
