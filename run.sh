#!/bin/bash
# Script de compilación, pruebas y ejecución para agentesp2 (Termostato y Grid 2D)

if command -v mvn &> /dev/null; then
    MVN="mvn"
elif [ -x "/usr/lib/netbeans/java/maven/bin/mvn" ]; then
    MVN="/usr/lib/netbeans/java/maven/bin/mvn"
elif [ -x "/snap/netbeans/current/netbeans/java/maven/bin/mvn" ]; then
    MVN="/snap/netbeans/current/netbeans/java/maven/bin/mvn"
else
    MVN="mvn"
fi

cd "$(dirname "$0")"

ACTION="${1:-grid}"
CHALLENGE_ARG="${2:-}"

case "$ACTION" in
    grid)
        echo "🧭 Ejecutando GridAgent (Navegación 2D)..."
        if [ -n "$CHALLENGE_ARG" ]; then
            $MVN compile exec:java -Dexec.args="grid $CHALLENGE_ARG"
        else
            $MVN compile exec:java -Dexec.args="grid"
        fi
        ;;
    termostato|thermostat)
        echo "🌡️ Ejecutando TermostatoAgent..."
        if [ -n "$CHALLENGE_ARG" ]; then
            $MVN compile exec:java -Dexec.args="termostato $CHALLENGE_ARG"
        else
            $MVN compile exec:java -Dexec.args="termostato"
        fi
        ;;
    test-unit)
        echo "🧪 Ejecutando tests unitarios offline (GridPositionTest y PathPlannerTest)..."
        $MVN test
        ;;
    test)
        echo "🔌 Ejecutando TestConexion (descubrimiento de retos en DBA Lab)..."
        $MVN compile exec:java -Dexec.mainClass="com.mycompany.agentesp2.TestConexion"
        ;;
    compile)
        echo "🔨 Compilando proyecto..."
        $MVN compile
        ;;
    main)
        echo "🚀 Ejecutando Main..."
        $MVN compile exec:java
        ;;
    clean)
        echo "🧹 Limpiando proyecto..."
        $MVN clean
        ;;
    *)
        echo "Uso: ./run.sh [grid [reto] | termostato [reto] | test-unit | test | compile | clean]"
        exit 1
        ;;
esac
