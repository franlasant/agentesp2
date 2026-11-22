package com.mycompany.agentesp2.grid.gui;

import com.mycompany.agentesp2.grid.navigation.CellType;
import com.mycompany.agentesp2.grid.navigation.MentalMap;
import com.mycompany.agentesp2.grid.navigation.PathPlanner;
import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridBumpers;
import com.mycompany.agentesp2.model.grid.GridEnergy;
import com.mycompany.agentesp2.model.grid.GridPosition;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Interfaz Gráfica Visual (Swing) para observar en tiempo real cómo el agente
 * descubre el mapa paso a paso (Niebla de Guerra, Fronteras, Visitadas, Obstáculos
 * y Plan en Cola A*).
 *
 * Funciona en dos modos:
 * 1. Simulador Interactivo Offline (./run.sh gui [1..10]): permite elegir cualquiera
 *    de los mapas de la práctica o mapas masivos, ejecutar paso a paso o en modo
 *    automático, ajustar la velocidad y activar/desactivar la vista de Rayos X.
 * 2. Monitor en Vivo desde GridAgent (./run.sh grid <reto>): se actualiza en vivo
 *    con cada percepción recibida del servidor DBA Lab.
 */
public class GridVisualizerGui extends JFrame {

    // Paleta de colores oscura moderna
    private static final Color BG_DARK = new Color(18, 22, 31);
    private static final Color PANEL_BG = new Color(28, 34, 48);
    private static final Color PANEL_BORDER = new Color(55, 65, 90);
    private static final Color TEXT_PRIMARY = new Color(235, 240, 250);
    private static final Color TEXT_MUTED = new Color(155, 168, 195);

    private static final Color COLOR_UNKNOWN = new Color(36, 43, 59);
    private static final Color COLOR_FREE_FRONTIER = new Color(46, 115, 160);
    private static final Color COLOR_VISITED = new Color(34, 72, 112);
    private static final Color COLOR_OBSTACLE = new Color(210, 65, 75);
    private static final Color COLOR_HIDDEN_WALL = new Color(95, 45, 55);
    private static final Color COLOR_AGENT = new Color(0, 215, 140);
    private static final Color COLOR_TARGET = new Color(255, 195, 0);
    private static final Color COLOR_PATH_TRAIL = new Color(0, 230, 180, 170);
    private static final Color COLOR_PLANNED_QUEUE = new Color(255, 150, 255, 210);

    /** Escenario predefinido para el simulador interactivo */
    public static final class MapScenario {
        final String name;
        final String[] rows;
        final GridPosition start;
        final GridPosition target;
        final int initialEnergy;

        MapScenario(String name, String[] rows, GridPosition start, GridPosition target, int initialEnergy) {
            this.name = name;
            this.rows = rows;
            this.start = start;
            this.target = target;
            this.initialEnergy = initialEnergy;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final boolean interactiveMode;
    private final List<MapScenario> scenarios;

    // Estado de simulación / visualización
    private String currentTitle = "Grid 2D";
    private MentalMap mentalMap = new MentalMap();
    private PathPlanner planner = new PathPlanner();
    private GridPosition startPosition = new GridPosition(0, 0);
    private GridPosition currentPosition = new GridPosition(0, 0);
    private GridPosition targetPosition = new GridPosition(9, 9);
    private GridBumpers currentBumpers = new GridBumpers(false, false, false, false);
    private int initialEnergy = 100;
    private int remainingEnergy = 100;
    private int stepCount = 0;
    private boolean finished = false;
    private String statusText = "LISTO";
    private final List<GridPosition> trajectory = new ArrayList<>();

    // Solo para modo simulador offline
    private int simWidth = 10;
    private int simHeight = 10;
    private final Set<GridPosition> realObstacles = new HashSet<>();
    private Timer autoTimer;

    // Componentes UI
    private final GridCanvas canvas = new GridCanvas();
    private JComboBox<MapScenario> scenarioCombo;
    private JButton playPauseBtn;
    private JButton stepBtn;
    private JButton resetBtn;
    private JCheckBox xrayCheck;
    private JCheckBox trailCheck;
    private JSlider speedSlider;

    private final JLabel lblStatus = new JLabel("LISTO");
    private final JLabel lblStep = new JLabel("0");
    private final JLabel lblPos = new JLabel("(0, 0)");
    private final JLabel lblTarget = new JLabel("(9, 9)");
    private final JLabel lblManhattan = new JLabel("0");
    private final JLabel lblAStarCalls = new JLabel("0");
    private final JLabel lblExpandedNodes = new JLabel("0");
    private final JLabel lblPlanQueue = new JLabel("0 pasos en cola");
    private final JProgressBar energyBar = new JProgressBar(0, 100);
    private final JLabel[] bumperLabels = new JLabel[4];
    private final JTextArea logArea = new JTextArea(10, 24);

    /**
     * Crea e inicia el monitor visual en vivo acoplado a una ejecución real de GridAgent.
     */
    public static GridVisualizerGui createLiveMonitor(String challengeName, GridPosition start, GridPosition target) {
        if (GraphicsEnvironment.isHeadless() || "false".equalsIgnoreCase(System.getenv("JADE_GUI"))) {
            return null;
        }
        final GridVisualizerGui[] holder = new GridVisualizerGui[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                GridVisualizerGui gui = new GridVisualizerGui(false, 0);
                gui.initLiveRun(challengeName, start, target);
                gui.setVisible(true);
                holder[0] = gui;
            });
        } catch (Exception e) {
            System.err.println("[GridVisualizerGui] No se pudo abrir la ventana gráfica: " + e.getMessage());
        }
        return holder[0];
    }

    /**
     * Lanza el simulador visual interactivo con selector de mapas.
     */
    public static void launchInteractive(int initialIndex) {
        SwingUtilities.invokeLater(() -> {
            GridVisualizerGui gui = new GridVisualizerGui(true, initialIndex);
            gui.setVisible(true);
        });
    }

    public static void main(String[] args) {
        int idx = 0;
        if (args != null && args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0].replace("#", "").trim());
                idx = Math.max(0, parsed - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        launchInteractive(idx);
    }

    public GridVisualizerGui(boolean interactiveMode, int initialScenarioIndex) {
        super("DBA Lab - Visualizador de Navegación Grid 2D (A* sobre Mapa Conocido)");
        this.interactiveMode = interactiveMode;
        this.scenarios = buildScenarios();

        setDefaultCloseOperation(interactiveMode ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 740));
        setPreferredSize(new Dimension(1220, 800));
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(10, 10));

        add(buildTopControlBar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(buildRightTelemetryPanel(), BorderLayout.EAST);
        add(buildBottomLegendBar(), BorderLayout.SOUTH);

        if (interactiveMode) {
            autoTimer = new Timer(220, e -> executeOneSimulatedStep());
            int validIdx = Math.min(Math.max(0, initialScenarioIndex), scenarios.size() - 1);
            scenarioCombo.setSelectedIndex(validIdx);
            loadScenario(scenarios.get(validIdx));
        }

        pack();
        setLocationRelativeTo(null);
    }

    // =========================================================================
    // CONSTRUCCIÓN DE LA INTERFAZ GRÁFICA
    // =========================================================================

    private JPanel buildTopControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        bar.setBackground(PANEL_BG);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, PANEL_BORDER));

        if (interactiveMode) {
            JLabel mapLbl = new JLabel("Mapa:");
            mapLbl.setForeground(TEXT_PRIMARY);
            mapLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            bar.add(mapLbl);

            scenarioCombo = new JComboBox<>(scenarios.toArray(new MapScenario[0]));
            scenarioCombo.setFont(new Font("SansSerif", Font.PLAIN, 13));
            scenarioCombo.addActionListener(e -> {
                MapScenario selected = (MapScenario) scenarioCombo.getSelectedItem();
                if (selected != null) {
                    stopAutoTimer();
                    loadScenario(selected);
                }
            });
            bar.add(scenarioCombo);

            playPauseBtn = createStyledButton("▶ Auto", new Color(25, 135, 84));
            playPauseBtn.addActionListener(e -> toggleAutoPlay());
            bar.add(playPauseBtn);

            stepBtn = createStyledButton("⏭ 1 Paso", new Color(13, 110, 253));
            stepBtn.addActionListener(e -> {
                stopAutoTimer();
                executeOneSimulatedStep();
            });
            bar.add(stepBtn);

            resetBtn = createStyledButton("🔄 Reiniciar", new Color(108, 117, 125));
            resetBtn.addActionListener(e -> {
                stopAutoTimer();
                MapScenario selected = (MapScenario) scenarioCombo.getSelectedItem();
                if (selected != null) {
                    loadScenario(selected);
                }
            });
            bar.add(resetBtn);

            JLabel speedLbl = new JLabel("Velocidad:");
            speedLbl.setForeground(TEXT_MUTED);
            bar.add(speedLbl);

            speedSlider = new JSlider(20, 700, 220);
            speedSlider.setInverted(true); // Derecha = más rápido (menor delay)
            speedSlider.setBackground(PANEL_BG);
            speedSlider.setPreferredSize(new Dimension(110, 24));
            speedSlider.addChangeListener(e -> {
                if (autoTimer != null) {
                    autoTimer.setDelay(speedSlider.getValue());
                }
            });
            bar.add(speedSlider);

            xrayCheck = new JCheckBox("Rayos X (ver muros ocultos)", true);
            xrayCheck.setBackground(PANEL_BG);
            xrayCheck.setForeground(TEXT_PRIMARY);
            xrayCheck.addActionListener(e -> canvas.repaint());
            bar.add(xrayCheck);
        } else {
            JLabel liveBadge = new JLabel("🔴 MONITOR EN VIVO - CONECTADO A GRIDAGENT (DBA LAB)");
            liveBadge.setForeground(new Color(0, 230, 160));
            liveBadge.setFont(new Font("SansSerif", Font.BOLD, 14));
            bar.add(liveBadge);
        }

        trailCheck = new JCheckBox("Mostrar rastro y plan en cola", true);
        trailCheck.setBackground(PANEL_BG);
        trailCheck.setForeground(TEXT_PRIMARY);
        trailCheck.addActionListener(e -> canvas.repaint());
        bar.add(trailCheck);

        return bar;
    }

    private JPanel buildRightTelemetryPanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(PANEL_BG);
        side.setPreferredSize(new Dimension(320, 600));
        side.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, PANEL_BORDER),
                new EmptyBorder(12, 14, 12, 14)
        ));

        // Tarjeta de estado y métricas
        JPanel metricsCard = new JPanel(new GridLayout(0, 2, 6, 6));
        metricsCard.setBackground(PANEL_BG);
        metricsCard.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(PANEL_BORDER), " Telemetría del Agente ",
                0, 0, new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED));

        styleValueLabel(lblStatus, new Color(0, 230, 160));
        styleValueLabel(lblStep, TEXT_PRIMARY);
        styleValueLabel(lblPos, new Color(0, 215, 140));
        styleValueLabel(lblTarget, COLOR_TARGET);
        styleValueLabel(lblManhattan, TEXT_PRIMARY);
        styleValueLabel(lblAStarCalls, new Color(110, 190, 255));
        styleValueLabel(lblExpandedNodes, new Color(110, 190, 255));
        styleValueLabel(lblPlanQueue, new Color(245, 160, 255));

        addMetricRow(metricsCard, "Estado:", lblStatus);
        addMetricRow(metricsCard, "Paso actual:", lblStep);
        addMetricRow(metricsCard, "Posición (@):", lblPos);
        addMetricRow(metricsCard, "Meta ($):", lblTarget);
        addMetricRow(metricsCard, "Dist. Manhattan:", lblManhattan);
        addMetricRow(metricsCard, "Llamadas a A*:", lblAStarCalls);
        addMetricRow(metricsCard, "Nodos expandidos:", lblExpandedNodes);
        addMetricRow(metricsCard, "Cola currentPlan:", lblPlanQueue);

        side.add(metricsCard);
        side.add(Box.createVerticalStrut(10));

        // Barra de batería / energía
        JPanel energyPanel = new JPanel(new BorderLayout(4, 4));
        energyPanel.setBackground(PANEL_BG);
        energyPanel.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(PANEL_BORDER), " Batería / Energía ",
                0, 0, new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED));
        energyBar.setStringPainted(true);
        energyBar.setForeground(new Color(40, 180, 99));
        energyBar.setBackground(new Color(45, 52, 70));
        energyBar.setFont(new Font("SansSerif", Font.BOLD, 12));
        energyPanel.add(energyBar, BorderLayout.CENTER);
        energyPanel.setMaximumSize(new Dimension(320, 62));
        side.add(energyPanel);
        side.add(Box.createVerticalStrut(10));

        // Sensores Bumpers (N, E, S, W)
        JPanel bumpersCard = new JPanel(new GridLayout(2, 2, 6, 6));
        bumpersCard.setBackground(PANEL_BG);
        bumpersCard.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(PANEL_BORDER), " Sensores Bumpers Locales ",
                0, 0, new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED));
        String[] bNames = {"NORTE ↑", "ESTE →", "SUR ↓", "OESTE ←"};
        for (int i = 0; i < 4; i++) {
            bumperLabels[i] = new JLabel(bNames[i] + ": LIBRE", SwingConstants.CENTER);
            bumperLabels[i].setOpaque(true);
            bumperLabels[i].setFont(new Font("SansSerif", Font.BOLD, 11));
            bumperLabels[i].setBorder(new EmptyBorder(5, 4, 5, 4));
            bumpersCard.add(bumperLabels[i]);
        }
        bumpersCard.setMaximumSize(new Dimension(320, 85));
        side.add(bumpersCard);
        side.add(Box.createVerticalStrut(10));

        // Registro de pasos
        logArea.setEditable(false);
        logArea.setBackground(new Color(16, 20, 28));
        logArea.setForeground(new Color(200, 215, 235));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(PANEL_BORDER), " Diario de Decisiones (see/next/action) ",
                0, 0, new Font("SansSerif", Font.BOLD, 12), TEXT_MUTED));
        side.add(scroll);

        return side;
    }

    private JPanel buildBottomLegendBar() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        legend.setBackground(PANEL_BG);
        legend.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, PANEL_BORDER));

        legend.add(createLegendItem(COLOR_AGENT, "Agente (@)"));
        legend.add(createLegendItem(COLOR_TARGET, "Meta ($)"));
        legend.add(createLegendItem(COLOR_VISITED, "Visitada (+ contador)"));
        legend.add(createLegendItem(COLOR_FREE_FRONTIER, "Frontera conocida (.)"));
        legend.add(createLegendItem(COLOR_OBSTACLE, "Muro descubierto (#)"));
        legend.add(createLegendItem(COLOR_UNKNOWN, "Niebla desconocida (?)"));
        legend.add(createLegendItem(COLOR_PLANNED_QUEUE, "Plan A* en cola"));

        return legend;
    }

    private JPanel createLegendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setBackground(PANEL_BG);
        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(14, 14));
        swatch.setBackground(color);
        swatch.setBorder(new LineBorder(Color.WHITE, 1));
        JLabel lbl = new JLabel(text);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.add(swatch);
        item.add(lbl);
        return item;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(bg.brighter(), 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        return btn;
    }

    private void styleValueLabel(JLabel lbl, Color color) {
        lbl.setForeground(color);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 12));
    }

    private void addMetricRow(JPanel parent, String title, JLabel valueLbl) {
        JLabel k = new JLabel(title);
        k.setForeground(TEXT_MUTED);
        k.setFont(new Font("SansSerif", Font.PLAIN, 12));
        parent.add(k);
        parent.add(valueLbl);
    }

    // =========================================================================
    // LÓGICA DEL SIMULADOR INTERACTIVO Y ACTUALIZACIÓN EN VIVO
    // =========================================================================

    public void loadScenario(MapScenario sc) {
        this.currentTitle = sc.name;
        this.simHeight = sc.rows.length;
        this.simWidth = sc.rows[0].length();
        this.realObstacles.clear();

        for (int y = 0; y < simHeight; y++) {
            for (int x = 0; x < simWidth; x++) {
                if (sc.rows[y].charAt(x) == '#') {
                    realObstacles.add(new GridPosition(x, y));
                }
            }
        }

        this.startPosition = sc.start;
        this.currentPosition = sc.start;
        this.targetPosition = sc.target;
        this.initialEnergy = sc.initialEnergy;
        this.remainingEnergy = sc.initialEnergy;
        this.stepCount = 0;
        this.finished = false;
        this.statusText = "EN CURSO";

        this.mentalMap = new MentalMap();
        this.mentalMap.registerTarget(targetPosition);
        this.planner = new PathPlanner();
        this.trajectory.clear();
        this.trajectory.add(currentPosition);

        // Percepción inicial en la casilla de salida (Fase SEE + NEXT inicial)
        this.currentBumpers = readSimulatedBumpers(currentPosition);
        this.mentalMap.updateFromPerceptions(currentPosition, currentBumpers);

        logArea.setText("");
        appendLog(String.format("▶ Cargado: %s%n  Inicio: %s -> Meta: %s (Batería: %d)",
                sc.name, startPosition, targetPosition, initialEnergy));
        refreshTelemetryWidgets();
        canvas.repaint();
    }

    private void toggleAutoPlay() {
        if (autoTimer == null) {
            return;
        }
        if (autoTimer.isRunning()) {
            stopAutoTimer();
        } else {
            if (finished) {
                MapScenario selected = (MapScenario) scenarioCombo.getSelectedItem();
                if (selected != null) {
                    loadScenario(selected);
                }
            }
            playPauseBtn.setText("⏸ Pausa");
            playPauseBtn.setBackground(new Color(210, 140, 20));
            autoTimer.start();
        }
    }

    private void stopAutoTimer() {
        if (autoTimer != null && autoTimer.isRunning()) {
            autoTimer.stop();
        }
        if (playPauseBtn != null) {
            playPauseBtn.setText("▶ Auto");
            playPauseBtn.setBackground(new Color(25, 135, 84));
        }
    }

    private void executeOneSimulatedStep() {
        if (finished) {
            stopAutoTimer();
            return;
        }
        if (currentPosition.equals(targetPosition)) {
            finished = true;
            statusText = "¡META ALCANZADA!";
            appendLog("★ ¡ÉXITO! Meta alcanzada en " + stepCount + " pasos.");
            stopAutoTimer();
            refreshTelemetryWidgets();
            canvas.repaint();
            return;
        }
        if (remainingEnergy <= 0) {
            finished = true;
            statusText = "SIN ENERGÍA";
            appendLog("❌ Batería agotada.");
            stopAutoTimer();
            refreshTelemetryWidgets();
            canvas.repaint();
            return;
        }

        int prevReplans = planner.getReplanCount();
        int prevExpanded = planner.getTotalExpandedNodes();
        int prevQueueSize = planner.getCurrentPlanSnapshot().size();

        Optional<GridAction> nextOpt = planner.findNextAction(currentPosition, targetPosition, mentalMap);
        if (nextOpt.isEmpty()) {
            finished = true;
            statusText = "PARADA CONTROLADA";
            appendLog("🛑 Sin ruta posible a la meta. Parada controlada sin colisión.");
            stopAutoTimer();
            refreshTelemetryWidgets();
            canvas.repaint();
            return;
        }

        GridAction action = nextOpt.get();
        String decisionOrigin;
        if (planner.getReplanCount() > prevReplans) {
            int expandedNow = planner.getTotalExpandedNodes() - prevExpanded;
            decisionOrigin = "A* Frontera (" + expandedNow + " nodos)";
        } else if (prevQueueSize > 0) {
            decisionOrigin = "Cola currentPlan O(1)";
        } else {
            decisionOrigin = "Avance Directo O(1)";
        }

        GridPosition nextPos = currentPosition.neighbor(action);
        stepCount++;
        remainingEnergy--;
        currentPosition = nextPos;
        trajectory.add(currentPosition);

        // Fase SEE + NEXT en la nueva casilla
        currentBumpers = readSimulatedBumpers(currentPosition);
        mentalMap.updateFromPerceptions(currentPosition, currentBumpers);

        appendLog(String.format("Paso %2d: %-10s -> %-7s [%s]",
                stepCount, action.name(), currentPosition, decisionOrigin));

        if (currentPosition.equals(targetPosition)) {
            finished = true;
            statusText = "¡META ALCANZADA!";
            appendLog("★ ¡ÉXITO! Meta alcanzada en " + stepCount + " pasos.");
            stopAutoTimer();
        }

        refreshTelemetryWidgets();
        canvas.repaint();
    }

    /**
     * Inicializa el monitor para un run en vivo de GridAgent.
     */
    public void initLiveRun(String challengeName, GridPosition start, GridPosition target) {
        this.currentTitle = challengeName;
        this.startPosition = start;
        this.currentPosition = start;
        this.targetPosition = target;
        this.stepCount = 0;
        this.finished = false;
        this.statusText = "ACTIVO EN DBA LAB";
        this.trajectory.clear();
        if (start != null) {
            this.trajectory.add(start);
        }
        logArea.setText("");
        appendLog("📡 Conectado a GridAgent: " + challengeName);
        refreshTelemetryWidgets();
        canvas.repaint();
    }

    /**
     * Actualiza la ventana gráfica en vivo desde GridAgent tras cada paso.
     */
    public void updateLiveState(int step, GridPosition current, GridPosition target,
                                GridBumpers bumpers, GridEnergy energy,
                                MentalMap map, PathPlanner pathPlanner, String status) {
        SwingUtilities.invokeLater(() -> {
            this.stepCount = step;
            this.currentPosition = current;
            this.targetPosition = target;
            this.currentBumpers = bumpers;
            this.mentalMap = map;
            this.planner = pathPlanner;
            if (status != null) {
                this.statusText = status;
            }
            if (energy != null) {
                if (step <= 1 && energy.getRemaining() > this.initialEnergy) {
                    this.initialEnergy = energy.getRemaining();
                } else if (step == 1) {
                    this.initialEnergy = Math.max(energy.getRemaining(), 1);
                }
                this.remainingEnergy = energy.getRemaining();
            }
            if (current != null && (trajectory.isEmpty() || !trajectory.get(trajectory.size() - 1).equals(current))) {
                trajectory.add(current);
            }
            appendLog(String.format("Paso %2d: Pos=%s | Bat=%d | A*=%d",
                    step, current, remainingEnergy, pathPlanner != null ? pathPlanner.getReplanCount() : 0));
            refreshTelemetryWidgets();
            canvas.repaint();
        });
    }

    private void refreshTelemetryWidgets() {
        lblStatus.setText(statusText);
        if (statusText.contains("META") || statusText.contains("SUCCEEDED")) {
            lblStatus.setForeground(new Color(0, 230, 160));
        } else if (statusText.contains("PARADA") || statusText.contains("SIN")) {
            lblStatus.setForeground(new Color(255, 195, 0));
        } else {
            lblStatus.setForeground(new Color(110, 195, 255));
        }

        lblStep.setText(String.valueOf(stepCount));
        lblPos.setText(currentPosition != null ? currentPosition.toString() : "-");
        lblTarget.setText(targetPosition != null ? targetPosition.toString() : "-");
        lblManhattan.setText((currentPosition != null && targetPosition != null)
                ? String.valueOf(currentPosition.manhattanDistance(targetPosition)) : "-");
        lblAStarCalls.setText(planner != null ? String.valueOf(planner.getReplanCount()) : "0");
        lblExpandedNodes.setText(planner != null ? String.valueOf(planner.getTotalExpandedNodes()) : "0");
        int queueLen = planner != null ? planner.getCurrentPlanSnapshot().size() : 0;
        lblPlanQueue.setText(queueLen + (queueLen == 1 ? " acción" : " acciones"));

        int maxE = Math.max(initialEnergy, Math.max(remainingEnergy, 1));
        energyBar.setMaximum(maxE);
        energyBar.setValue(Math.max(0, remainingEnergy));
        energyBar.setString(remainingEnergy + " / " + maxE + " uds");

        if (currentBumpers != null) {
            GridAction[] dirs = {GridAction.MOVE_NORTH, GridAction.MOVE_EAST, GridAction.MOVE_SOUTH, GridAction.MOVE_WEST};
            String[] names = {"NORTE ↑", "ESTE →", "SUR ↓", "OESTE ←"};
            for (int i = 0; i < 4; i++) {
                boolean blocked = currentBumpers.isBlocked(dirs[i]);
                bumperLabels[i].setText(names[i] + (blocked ? ": MURO #" : ": LIBRE"));
                bumperLabels[i].setBackground(blocked ? new Color(165, 42, 52) : new Color(32, 95, 68));
                bumperLabels[i].setForeground(Color.WHITE);
            }
        }
    }

    private void appendLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private GridBumpers readSimulatedBumpers(GridPosition pos) {
        return new GridBumpers(
                isSimulatedBlocked(pos.neighbor(GridAction.MOVE_NORTH)),
                isSimulatedBlocked(pos.neighbor(GridAction.MOVE_EAST)),
                isSimulatedBlocked(pos.neighbor(GridAction.MOVE_SOUTH)),
                isSimulatedBlocked(pos.neighbor(GridAction.MOVE_WEST))
        );
    }

    private boolean isSimulatedBlocked(GridPosition pos) {
        if (pos.getX() < 0 || pos.getX() >= simWidth || pos.getY() < 0 || pos.getY() >= simHeight) {
            return true;
        }
        return realObstacles.contains(pos);
    }

    // =========================================================================
    // LIENZO DE RENDERIZADO 2D DEL MAPA (GRID CANVAS)
    // =========================================================================

    private class GridCanvas extends JPanel {

        GridCanvas() {
            setBackground(BG_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int minX = 0;
            int maxX = interactiveMode ? simWidth - 1 : Math.max(9, mentalMap.getMaxX());
            int minY = 0;
            int maxY = interactiveMode ? simHeight - 1 : Math.max(9, mentalMap.getMaxY());

            if (!interactiveMode) {
                minX = Math.min(0, mentalMap.getMinX());
                minY = Math.min(0, mentalMap.getMinY());
            }

            int cols = Math.max(1, maxX - minX + 1);
            int rows = Math.max(1, maxY - minY + 1);

            int pad = 34;
            int availW = Math.max(100, getWidth() - pad * 2);
            int availH = Math.max(100, getHeight() - pad * 2);
            int cellSize = Math.max(10, Math.min(availW / cols, availH / rows));

            int gridW = cols * cellSize;
            int gridH = rows * cellSize;
            int originX = (getWidth() - gridW) / 2;
            int originY = (getHeight() - gridH) / 2;

            boolean showXray = interactiveMode && xrayCheck != null && xrayCheck.isSelected();
            boolean showTrail = trailCheck != null && trailCheck.isSelected();
            Map<GridPosition, CellType> known = mentalMap.getKnownCells();

            // 1. Dibujar celdas del Grid
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    GridPosition pos = new GridPosition(x, y);
                    int px = originX + (x - minX) * cellSize;
                    int py = originY + (y - minY) * cellSize;

                    CellType type = known.getOrDefault(pos, CellType.UNKNOWN);
                    int visits = mentalMap.getVisitCount(pos);

                    // Color base de la casilla
                    if (type == CellType.OBSTACLE) {
                        g2.setColor(COLOR_OBSTACLE);
                    } else if (type == CellType.FREE) {
                        g2.setColor(visits > 0 ? COLOR_VISITED : COLOR_FREE_FRONTIER);
                    } else {
                        if (showXray && realObstacles.contains(pos)) {
                            g2.setColor(COLOR_HIDDEN_WALL);
                        } else {
                            g2.setColor(COLOR_UNKNOWN);
                        }
                    }

                    int arc = cellSize >= 20 ? 6 : 2;
                    g2.fillRoundRect(px + 1, py + 1, cellSize - 2, cellSize - 2, arc, arc);

                    // Borde sutil
                    g2.setColor(new Color(22, 27, 38));
                    g2.drawRoundRect(px + 1, py + 1, cellSize - 2, cellSize - 2, arc, arc);

                    // Indicadores dentro de la celda (si el tamaño lo permite)
                    if (cellSize >= 22) {
                        if (type == CellType.UNKNOWN) {
                            g2.setColor(showXray && realObstacles.contains(pos)
                                    ? new Color(180, 110, 120, 150)
                                    : new Color(110, 125, 150, 110));
                            drawCenteredText(g2, showXray && realObstacles.contains(pos) ? "#" : "?",
                                    px, py, cellSize, new Font("SansSerif", Font.BOLD, Math.max(10, cellSize / 3)));
                        } else if (type == CellType.OBSTACLE) {
                            g2.setColor(new Color(255, 210, 215));
                            drawCenteredText(g2, "#", px, py, cellSize,
                                    new Font("SansSerif", Font.BOLD, Math.max(11, cellSize / 2)));
                        } else if (type == CellType.FREE && visits > 0 && !pos.equals(currentPosition) && !pos.equals(targetPosition)) {
                            g2.setColor(new Color(180, 220, 255, 200));
                            drawCenteredText(g2, String.valueOf(visits), px, py, cellSize,
                                    new Font("SansSerif", Font.BOLD, Math.max(10, cellSize / 3)));
                        } else if (type == CellType.FREE && visits == 0 && !pos.equals(targetPosition)) {
                            // Punto de frontera descubierta
                            int dot = Math.max(5, cellSize / 5);
                            g2.setColor(new Color(150, 230, 255, 200));
                            g2.fillOval(px + (cellSize - dot) / 2, py + (cellSize - dot) / 2, dot, dot);
                        }
                    }
                }
            }

            // 2. Dibujar muros exteriores ya descubiertos fuera de [minX..maxX] (bordes percibidos)
            g2.setColor(COLOR_OBSTACLE);
            for (Map.Entry<GridPosition, CellType> entry : known.entrySet()) {
                if (entry.getValue() == CellType.OBSTACLE) {
                    GridPosition p = entry.getKey();
                    if (p.getX() == minX - 1 && p.getY() >= minY && p.getY() <= maxY) {
                        int py = originY + (p.getY() - minY) * cellSize;
                        g2.fillRect(originX - 4, py + 2, 4, cellSize - 4);
                    } else if (p.getX() == maxX + 1 && p.getY() >= minY && p.getY() <= maxY) {
                        int py = originY + (p.getY() - minY) * cellSize;
                        g2.fillRect(originX + gridW, py + 2, 4, cellSize - 4);
                    } else if (p.getY() == minY - 1 && p.getX() >= minX && p.getX() <= maxX) {
                        int px = originX + (p.getX() - minX) * cellSize;
                        g2.fillRect(px + 2, originY - 4, cellSize - 4, 4);
                    } else if (p.getY() == maxY + 1 && p.getX() >= minX && p.getX() <= maxX) {
                        int px = originX + (p.getX() - minX) * cellSize;
                        g2.fillRect(px + 2, originY + gridH, cellSize - 4, 4);
                    }
                }
            }

            // 3. Dibujar el rastro histórico (trayectoria seguida por el agente)
            if (showTrail && trajectory.size() > 1) {
                g2.setColor(COLOR_PATH_TRAIL);
                g2.setStroke(new BasicStroke(Math.max(2f, cellSize / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Float();
                for (int i = 0; i < trajectory.size(); i++) {
                    GridPosition p = trajectory.get(i);
                    float cx = originX + (p.getX() - minX + 0.5f) * cellSize;
                    float cy = originY + (p.getY() - minY + 0.5f) * cellSize;
                    if (i == 0) {
                        path.moveTo(cx, cy);
                    } else {
                        path.lineTo(cx, cy);
                    }
                }
                g2.draw(path);
            }

            // 4. Dibujar el plan en cola actual de A* (currentPlan)
            if (showTrail && planner != null && currentPosition != null) {
                List<GridAction> queued = planner.getCurrentPlanSnapshot();
                if (!queued.isEmpty()) {
                    g2.setColor(COLOR_PLANNED_QUEUE);
                    g2.setStroke(new BasicStroke(Math.max(2f, cellSize / 9f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 6f}, 0f));
                    GridPosition cursor = currentPosition;
                    for (GridAction act : queued) {
                        GridPosition next = cursor.neighbor(act);
                        int x1 = (int) (originX + (cursor.getX() - minX + 0.5f) * cellSize);
                        int y1 = (int) (originY + (cursor.getY() - minY + 0.5f) * cellSize);
                        int x2 = (int) (originX + (next.getX() - minX + 0.5f) * cellSize);
                        int y2 = (int) (originY + (next.getY() - minY + 0.5f) * cellSize);
                        g2.drawLine(x1, y1, x2, y2);
                        int r = Math.max(4, cellSize / 5);
                        g2.fillOval(x2 - r / 2, y2 - r / 2, r, r);
                        cursor = next;
                    }
                }
            }

            // 5. Dibujar la Meta ($)
            if (targetPosition != null) {
                int tx = originX + (targetPosition.getX() - minX) * cellSize;
                int ty = originY + (targetPosition.getY() - minY) * cellSize;
                int padT = Math.max(2, cellSize / 7);
                g2.setColor(COLOR_TARGET);
                g2.fillRoundRect(tx + padT, ty + padT, cellSize - padT * 2, cellSize - padT * 2, 10, 10);
                g2.setColor(new Color(30, 25, 10));
                drawCenteredText(g2, "$", tx, ty, cellSize,
                        new Font("SansSerif", Font.BOLD, Math.max(11, cellSize / 2)));
            }

            // 6. Dibujar al Agente (@)
            if (currentPosition != null) {
                int ax = originX + (currentPosition.getX() - minX) * cellSize;
                int ay = originY + (currentPosition.getY() - minY) * cellSize;
                int padA = Math.max(2, cellSize / 7);
                g2.setColor(COLOR_AGENT);
                g2.fillOval(ax + padA, ay + padA, cellSize - padA * 2, cellSize - padA * 2);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(ax + padA, ay + padA, cellSize - padA * 2, cellSize - padA * 2);
                g2.setColor(new Color(10, 30, 20));
                String symbol = currentPosition.equals(targetPosition) ? "★" : "@";
                drawCenteredText(g2, symbol, ax, ay, cellSize,
                        new Font("SansSerif", Font.BOLD, Math.max(11, cellSize / 2)));
            }

            // 7. Numeración de ejes X e Y (si las celdas son suficientemente grandes)
            if (cellSize >= 18) {
                g2.setColor(TEXT_MUTED);
                g2.setFont(new Font("Monospaced", Font.PLAIN, Math.min(12, Math.max(9, cellSize / 3))));
                for (int x = minX; x <= maxX; x++) {
                    if (cols > 25 && x % 5 != 0) continue;
                    int px = originX + (x - minX) * cellSize;
                    drawCenteredText(g2, String.valueOf(x), px, originY - 22, cellSize, g2.getFont());
                }
                for (int y = minY; y <= maxY; y++) {
                    if (rows > 25 && y % 5 != 0) continue;
                    int py = originY + (y - minY) * cellSize;
                    drawCenteredText(g2, String.valueOf(y), originX - 26, py, 24, cellSize, g2.getFont());
                }
            }

            g2.dispose();
        }

        private void drawCenteredText(Graphics2D g2, String text, int x, int y, int size, Font font) {
            drawCenteredText(g2, text, x, y, size, size, font);
        }

        private void drawCenteredText(Graphics2D g2, String text, int x, int y, int w, int h, Font font) {
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (w - fm.stringWidth(text)) / 2;
            int ty = y + ((h - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(text, tx, ty);
        }
    }

    // =========================================================================
    // CATÁLOGO DE MAPAS DE LA PRÁCTICA + MAPAS COMPLEJOS Y MASIVOS
    // =========================================================================

    private static List<MapScenario> buildScenarios() {
        List<MapScenario> list = new ArrayList<>();

        list.add(new MapScenario("1. Campo abierto (10x10)", new String[]{
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        }, new GridPosition(0, 0), new GridPosition(9, 9), 50));

        list.add(new MapScenario("2. Muro vertical (10x10)", new String[]{
            "..........",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            "....#.....",
            ".........."
        }, new GridPosition(1, 5), new GridPosition(8, 5), 60));

        list.add(new MapScenario("3. Muro horizontal (10x10)", new String[]{
            "..........",
            "..........",
            "..........",
            "..........",
            ".########.",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        }, new GridPosition(5, 1), new GridPosition(5, 8), 60));

        list.add(new MapScenario("4. Muro diagonal (10x10)", new String[]{
            "..........",
            ".......#..",
            "......#...",
            ".....#....",
            "....#.....",
            "...#......",
            "..#.......",
            ".#........",
            "..........",
            ".........."
        }, new GridPosition(1, 1), new GridPosition(8, 8), 80));

        list.add(new MapScenario("5. Barrera en U (10x10)", new String[]{
            "..........",
            "..........",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..######..",
            "..........",
            "..........",
            ".........."
        }, new GridPosition(4, 1), new GridPosition(4, 8), 100));

        list.add(new MapScenario("6. Barrera en C (10x10)", new String[]{
            "..........",
            "..........",
            "..#####...",
            "......#...",
            "......#...",
            "......#...",
            "......#...",
            "..#####...",
            "..........",
            ".........."
        }, new GridPosition(1, 4), new GridPosition(8, 4), 100));

        list.add(new MapScenario("7. Barrera en arco (10x10)", new String[]{
            "..........",
            "..........",
            "...####...",
            "..#....#..",
            "..#....#..",
            "..#....#..",
            "..........",
            "..........",
            "..........",
            ".........."
        }, new GridPosition(4, 5), new GridPosition(4, 0), 100));

        list.add(new MapScenario("8. Meta encerrada - Inalcanzable (10x10)", new String[]{
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".......###",
            ".......#.#",
            ".......###"
        }, new GridPosition(0, 0), new GridPosition(8, 8), 200));

        list.add(new MapScenario("9. Laberinto serpentín y trampas (20x20)", new String[]{
            "....................",
            "....#...............",
            "....#..##########...",
            "....#...........#...",
            "....#########...#...",
            "............#...#...",
            "..#######...#...#...",
            "..#.....#...#...#...",
            "..#.....#...#...#...",
            "..#..####...#...#...",
            "..#.........#...#...",
            "..###########...#...",
            "................#...",
            "...##############...",
            "...#................",
            "...#..###########...",
            "...#............#...",
            "...##########...#...",
            "................#...",
            "...................."
        }, new GridPosition(1, 1), new GridPosition(18, 18), 400));

        // 10. Mapa Masivo 40x40 con barreras múltiples
        int size = 40;
        String[] massive = new String[size];
        for (int y = 0; y < size; y++) {
            StringBuilder row = new StringBuilder(size);
            for (int x = 0; x < size; x++) {
                boolean isWall = (x == 12 && y >= 2 && y <= 34)
                        || (y == 24 && x >= 6 && x <= 28)
                        || (x == 28 && y >= 6 && y <= 37)
                        || (x >= 18 && x <= 23 && y == 12)
                        || (x == 23 && y >= 12 && y <= 19);
                row.append(isWall ? '#' : '.');
            }
            massive[y] = row.toString();
        }
        list.add(new MapScenario("10. Mapa Masivo con barreras (40x40)", massive,
                new GridPosition(2, 20), new GridPosition(37, 20), 600));

        return list;
    }
}
