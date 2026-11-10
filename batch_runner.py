import os
import subprocess
import sys
import time
import re

CHALLENGES = [
    (9,  "Fase 2 - 01 - Corredores horizontales"),
    (10, "Fase 2 - 02 - Corredores verticales"),
    (11, "Fase 2 - 03 - Barrera triangular"),
    (12, "Fase 2 - 04 - Recintos conectados"),
    (13, "Fase 2 - 05 - Barreras en U y L"),
    (14, "Fase 2 - 06 - Laberinto central"),
    (15, "Fase 2 - 07 - Bloques conectados"),
    (16, "Fase 2 - 08 - Disco con hendidura"),
    (17, "Fase 2 - 09 - Pasillos estrechos"),
    (18, "Fase 2 - 10 - Obstáculos dispersos A"),
    (19, "Fase 2 - 11 - Obstáculos dispersos B"),
]

os.makedirs("logs_fase2", exist_ok=True)
results = []

print("================================================================================")
print("   INICIANDO BATERÍA DE PRUEBAS EN VIVO: FASE 2 (11 RETOS NUEVOS DE GRID 2D)   ")
print("================================================================================")

for cid, cname in CHALLENGES:
    log_path = f"logs_fase2/reto_{cid}.log"
    print(f"\n>>> [ID {cid:2d}] Ejecutando: {cname}...")
    start_time = time.time()
    
    env = os.environ.copy()
    env["JADE_GUI"] = "false"
    
    cmd = ["./run.sh", "grid", f"id:{cid}"]
    with open(log_path, "w", encoding="utf-8") as log_file:
        proc = subprocess.run(cmd, stdout=log_file, stderr=subprocess.STDOUT, env=env)
    
    elapsed = time.time() - start_time
    
    # Parse log for result
    with open(log_path, "r", encoding="utf-8", errors="ignore") as log_file:
        content = log_file.read()
    
    run_id_match = re.search(r"FIN DEL RUN (\d+)", content)
    run_id = run_id_match.group(1) if run_id_match else "N/A"
    
    status_match = re.search(r"Estado final:\s*([A-Za-z_]+)", content)
    if not status_match:
        status_match = re.search(r"FIN DEL RUN \d+.*?:\s*([A-Za-z_]+)", content)
    status = status_match.group(1) if status_match else ("SUCCEEDED" if "TARGET_REACHED" in content else "FAILED")
    
    reason_match = re.search(r"Razón:\s*([A-Za-z_]+)", content)
    reason = reason_match.group(1) if reason_match else ("TARGET_REACHED" if "TARGET_REACHED" in content else "UNKNOWN")
    
    steps_match = re.search(r"Pasos ejecutados:\s*(\d+)", content)
    steps = steps_match.group(1) if steps_match else "N/A"
    
    energy_match = re.search(r"Última energía leída:\s*([^\n]+)", content)
    energy = energy_match.group(1) if energy_match else "N/A"
    
    symbol = "✅" if "SUCCEEDED" in status or "TARGET_REACHED" in reason else "❌"
    print(f"    {symbol} Resultado: {status} ({reason}) | Pasos: {steps} | Energía: {energy} | Run ID: {run_id} | Tiempo: {elapsed:.1f}s")
    
    results.append({
        "id": cid,
        "name": cname,
        "run_id": run_id,
        "status": status,
        "reason": reason,
        "steps": steps,
        "energy": energy,
        "time": f"{elapsed:.1f}s"
    })
    
    time.sleep(1.5)

print("\n" + "=" * 80)
print("                   RESUMEN GENERAL DE LA FASE 2 EN DBA LAB                      ")
print("=" * 80)
print(f"{'ID':>4} | {'Nombre del Reto':<35} | {'Estado':<10} | {'Pasos':>6} | {'Energía':<12} | {'Run ID':>6}")
print("-" * 80)
for r in results:
    print(f"{r['id']:>4} | {r['name']:<35} | {r['status']:<10} | {r['steps']:>6} | {r['energy']:<12} | {r['run_id']:>6}")
print("=" * 80)
