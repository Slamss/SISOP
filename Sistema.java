// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Integrantes do Grupo: Edson Ricardo da Costa, Jonatas Van Groll Lemos e Lourenço Souza.
//
// Fase 1 - máquina virtual
// Fase 2 - interrupções
// Fase 3 - chamadas de sistema
// Fase 4 - Gerente de Memória
// Fase 5 - Gerente de Processo
// Fase 6 - IO Concorrente
// Fase 7 - Shell Concorrente 


import java.util.*;
import java.util.concurrent.Semaphore;

public class Sistema {
	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW ---------------------------------------------- 
	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A -  definicoes de opcode e palavra de memoria ---------------------- 
	public class Word { 		// cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; 		//
		public int r1; 			// indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; 			// indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; 			// parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) {  
			opc = _opc;   r1 = _r1;    r2 = _r2;	p = _p;
		}
	}
    // -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
    // --------------------- C P U  -  definicoes da CPU ----------------------------------------------------- 
	public enum Opcode {
		DATA, ___,		                                                         // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE,  JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,   // desvios e parada
		ADDI, SUBI,  ADD, SUB, MULT,                                            // matematicos
		LDI, LDD, STD,LDX, STX, SWAP,                                           // movimentacao
		TRAP;                                                                   // usada para uma interrupção do software
	}

	public enum Interruptions {
		interruptionInvalidAddress,      // ocorre se o programa do usuário tentar acessar um endereço fora da memória
		interruptionInvalidInstruction,  // ocorre se a instrução carregada for inválida
		interruptionOverflowOperation,   // ocorre quando acontece um overflow em uma operação matemática
		interruptionStop,                // ocorre quando o programa se encerra
		interruptionNone,                // interrupção padrão, ocorre quando não há interrupção
		interruptionInvalidPageAccessed, // interrupção de pagina inválida, ocorre quando tentamos traduzir de memoria logica para fisica e acessamos uma pagina invalida 
		interruptionSchedulerClock,      // interrupção de escolonamento, ocorre quando temos um problema no escalonador
		interruptionConcorrenteIO;       // interrupção de ...
	}

	public static final int pageLength = 16;                      // voce pode configurar a quantidade da pagina apenas alterando o valor aqui
	public static boolean enableProcessMenagerWarnings = false;   // essa variável controla se os avisos do gerenciador de processos serão exibidos ou não

	public class CPU extends Thread {
									// característica do processador: contexto da CPU ...
		private int pc; 			// ... composto de  counter,
		private Word ir; 			// instruction register,
		private int[] reg;       	// registradores da CPU
		private Semaphore cpuSemaphore;     // semaforo responsável por controlar a execução da cpu

		private Interruptions interruptions;              // CPU instancia as interrupções 
		private InterruptionsHandler interruptionHandler; // CPU instancia o manipulador de exceções
		private TrapHandler trapHandler;                  // CPU instancia o manipulador de traps para chamadas de sistema

		private int maxMemoryBorder; // armazena o valor máximo da borda da memória
		private int minMemoryBorder; // armazena o valor mínimo da borda da memória

		private int schedulerRunningClock = 0;   // armazena o clock atual do scheduler em que o programa está rodando
		final private int schedulerMaxClock = 5; // voce pode configurar a quantidade de execuções que o clock irá fazer em cada processo

		private int[] runningPageTable; // armazena as paginas de execução de um processo
		public boolean isCpuAtiva = false; // armazena se a cpu está ativa ou não			

		private Word[] m;   // CPU acessa MEMORIA, guarda referencia 'm' a ela. memoria nao muda. ee sempre a mesma.

		public CPU(Word[] _m, InterruptionsHandler interruptionHandler, TrapHandler trapHandler, Semaphore cpuSemaphore) {  // referencia a MEMORIA, interruptions handler e trap handler passada na criacao da CPU
			m = _m; 				                     // usa o atributo 'm' para acessar a memoria
			reg = new int[10]; 		                     // aloca o espaço para os registradores
			this.interruptionHandler = interruptionHandler;  // associa o manipulador de exceções
			this.trapHandler = trapHandler;                  // associa o manipulador de de trap para chamadas de sistema
			this.cpuSemaphore = cpuSemaphore;
		}

		public void setContext(int _pc, int minMemoryBorder, int maxMemoryBorder, int[] runningPageTable) {  // no futuro esta funcao vai ter que ser
			pc = _pc;                                         // limite e pc (deve ser zero nesta versao)
			interruptions = Interruptions.interruptionNone;   // ajusta o interruptions para NONE
			this.minMemoryBorder = minMemoryBorder;           // ajusta o valor da borda mínima da memóia
			this.maxMemoryBorder = maxMemoryBorder;           // ajusta o valor da borda máxima da memória		
			this.runningPageTable = runningPageTable;     // ajusta a quantidade de paginas da tabela
		}
	
        // public void showState(){
		// 	System.out.println("       "+ pc); 
		// 	  System.out.print("           ");
		// 	for (int i=0; i<10; i++) { System.out.print("r"+i);   System.out.print(": "+reg[i]+"     "); };  
		// 	System.out.println("");
		// 	System.out.print("           ");  aux.dump(ir);
	    // }

		private boolean isMemoryAddressValid(int memoryAddress) {                     // verifica se o o valor passado é válido
			if (memoryAddress < minMemoryBorder || memoryAddress > maxMemoryBorder) { // se o valor passado for menor que o valor minimo da memória ou o valor passado for maior que o valor maximo da memória
				interruptions = Interruptions.interruptionInvalidAddress;             // ajustamos o valor da interruption para o valor correspondente ao valor de endereço de memória inválido
                                                                                      //
				return false;														  //
																					  //
			}                                                                         //
			return true;															  // caso esteja dentro do tamanho, retornamos que está tudo bem em acessar aquele endereço de memória
		}

		public int translateToPhysicalMemoryAddress(int logicMemoryAddress) { // método desenvolvido conforme recomendação do PDF do enunciado da fase 4
			int page = logicMemoryAddress/pageLength;                         // tópico 1.4 utilizado como referência
			int offset = logicMemoryAddress%pageLength;
			int physicalMemoryAddress;
			try {
				physicalMemoryAddress = runningPageTable[page]*pageLength+offset;
			} catch(IndexOutOfBoundsException e) {
				interruptions = Interruptions.interruptionInvalidPageAccessed;
				return -1;
			}
			return physicalMemoryAddress;
		}

		public void run() { 		// execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente setado
			while (true) {
				boolean trapOcorreu = false;
				try {
					cpuSemaphore.acquire();  // tenta dar acquire no semaforo da CPU para realizar o processo
				} catch (Exception e) {
					System.out.println("!!! Semaphoro sendo utilizado, ocorreu um erro ao tentar dar acquire !!!"); // caso ocorra algum erro no acquire ele exibe a mensagem
				}
				while (true) { 			// ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
					// FETCH
					if (isMemoryAddressValid(translateToPhysicalMemoryAddress(pc))) {  // verifica se o valor de memória é válido
						ir = m[translateToPhysicalMemoryAddress(pc)]; 	// busca posicao da memoria apontada por pc, guarda em ir
						//if debug
						//showState();
						// EXECUTA INSTRUCAO NO ir
						switch (ir.opc) { // para cada opcode, sua execução
							case LDI: // Rd ← k
								reg[ir.r1] = ir.p;
								pc++;
								break;

							case STD: // [A] ← Rs
								if (!isMemoryAddressValid(translateToPhysicalMemoryAddress(ir.p))) { 
									interruptions = Interruptions.interruptionInvalidAddress; // precisamos tratar antes pois de executar, pois se deixar para depois o java irá para o programa por causa da exceção do próprio java
									break;
								}
								m[translateToPhysicalMemoryAddress(ir.p)].opc = Opcode.DATA;
								m[translateToPhysicalMemoryAddress(ir.p)].p = reg[ir.r1];
								pc++;
								break;

							case ADD: // Rd ← Rd + Rs
								try {
									reg[ir.r1] = Math.addExact(reg[ir.r1], reg[ir.r2]);
								} catch(ArithmeticException exception){
									interruptions = Interruptions.interruptionOverflowOperation; // precisamos tratar pois caso ocorra alguma exceção em uma operação matemática precisamos alterar a interruptions para uma interrupção de overflow
								}	
								pc++;
								break;

							case MULT: // Rd ← Rd * Rs
								try {
									reg[ir.r1] = Math.multiplyExact(reg[ir.r1], reg[ir.r2]);
								} catch (ArithmeticException exception) {
									interruptions = Interruptions.interruptionOverflowOperation; // precisamos tratar pois caso ocorra alguma exceção em uma operação matemática precisamos alterar a interruptions para uma interrupção de overflow
								}							
								pc++;
								break;

							case ADDI: // Rd ← Rd + k
								try {
									reg[ir.r1] = Math.addExact(reg[ir.r1], ir.p);
								} catch(ArithmeticException exception){
									interruptions = Interruptions.interruptionOverflowOperation; // precisamos tratar pois caso ocorra alguma exceção em uma operação matemática precisamos alterar a interruptions para uma interrupção de overflow
								}			
								pc++;
								break;

							case STX: // [Rd] ←Rs
								if (!isMemoryAddressValid(translateToPhysicalMemoryAddress(reg[ir.r1]))) {
									interruptions = Interruptions.interruptionInvalidAddress; // precisamos tratar antes pois de executar, pois se deixar para depois o java irá para o programa por causa da exceção do próprio java
								}
								m[translateToPhysicalMemoryAddress(reg[ir.r1])].opc = Opcode.DATA;      
								m[translateToPhysicalMemoryAddress(reg[ir.r1])].p = reg[ir.r2];          
								pc++;
								break;

							case SUB: // Rd ← Rd - Rs
								try { 
									reg[ir.r1] = Math.subtractExact(reg[ir.r1], reg[ir.r2]);
								} catch(ArithmeticException exception){
									interruptions = Interruptions.interruptionOverflowOperation;  // precisamos tratar pois caso ocorra alguma exceção em uma operação matemática precisamos alterar a interruptions para uma interrupção de overflow
								}							
								pc++;
								break;

							case JMP: //  PC ← k
								if (!isMemoryAddressValid(translateToPhysicalMemoryAddress(ir.p))){ 
									interruptions = Interruptions.interruptionInvalidAddress;
								}
								pc = ir.p;
								break;
							
							case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
								if (reg[ir.r2] > 0) {
									pc = reg[ir.r1];
								} else {
									pc++;
								}
								break;

							case JMPIE: // If Rc = 0 Then PC ← Rs Else PC ← PC +1
								if (reg[ir.r2] == 0) {
									pc = reg[ir.r1];
								} else {
									pc++;
								}
								break;

							case STOP: // por enquanto, para execucao
								interruptions = Interruptions.interruptionStop; // adiciona a interrupção de stop caso tenha uma parada
								break;

							case TRAP:
								trapOcorreu = true;
								pc++;
								trapHandler.trap(this);
								break;

							default:
								interruptions = Interruptions.interruptionInvalidInstruction;
								break;
						}
						schedulerRunningClock++;
						if  (schedulerRunningClock >= schedulerMaxClock) {
							interruptions = Interruptions.interruptionSchedulerClock; // adiciona a interrupção do escalonador caso tenha um numero de clocks maior ou igual ao limite definido
						}
					}
					// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
					if (!(interruptions == Interruptions.interruptionNone)) {
						interruptionHandler.handle(interruptions); // pega a interruption armazenada e manda ela para o manipulador de interrupções
						if(interruptions != Interruptions.interruptionConcorrenteIO) { // se a interruption nao for a do IO
							break; // break sai do loop da cpu
						}
					}
					if(trapOcorreu) {
						break; // break sai do loop da cpu
					}
				}
			}
		}
	}
    // ------------------ C P U - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------	
    // ------------------- V M  - constituida de CPU e MEMORIA -----------------------------------------------
    // -------------------------- atributos e construcao da VM -----------------------------------------------
	public class VM {
		public int tamMem;                           // tamanho da memoria
        public Word[] m;                             // lista de palavra
		public CPU cpu;                              // cpu do sistema
		public MemoryManager memoryManager;          // gerente de memoria
		public ProcessManager processManager;        // gerente de processo
		public IoMenager ioManager;                  // gerente de IO
		public Semaphore menuSemaphore = new Semaphore(0); // semaforo responsavel por controlar o menu
		public Semaphore cpuSemaphore = new Semaphore(0);  // semaforo responsável por controlar a execução da cpu

        public VM(InterruptionsHandler interruptionHandler, TrapHandler trapHandler) {   // vm deve ser configurada com endereço de tratamento de interrupcoes
			// memória
  		 	 tamMem = 1024;
			 m = new Word[tamMem]; // m ee a memoria
			 for (int i=0; i<tamMem; i++) { m[i] = new Word(Opcode.___,-1,-1,-1); };
	  	 	 // cpu
			 cpu = new CPU(m, interruptionHandler, trapHandler, cpuSemaphore);

			 cpu.minMemoryBorder = 0;
			 cpu.maxMemoryBorder = tamMem - 1;			 

			 ioManager = new IoMenager(m);
			 memoryManager = new MemoryManager(m);
			 processManager = new ProcessManager(cpu, memoryManager);
	    }	
	}
    // ------------------- V M  - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
    // --------------------H A R D W A R E - fim -------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio ----------------------------------------------------------
	public enum ProcessState {
		FINISHED, BLOCKED, RUNNING, READY
	}

	public class InterruptionsHandler {
		public void handle(Interruptions interruptions) {
			switch (interruptions) {
				case interruptionStop:
					handleStop(interruptions);
					break;
				case interruptionSchedulerClock:
					handleScheduler(interruptions);
					break;
				case interruptionConcorrenteIO:
					handleIO(interruptions);					
					break;
				default:
					defaultInterruptionCase(interruptions);
					break;
			}			
		}

		public void handleStop(Interruptions interruptions) {
			if (enableProcessMenagerWarnings) {
				System.out.println("Processo Finalizado(" + interruptions + ")!!!");
				System.out.println("----------------------------------------------------- ");
			}
			vm.cpu.schedulerRunningClock = 0;
			vm.processManager.stopProcess();
		}

		public void handleScheduler (Interruptions interruptions) {
			if (enableProcessMenagerWarnings) {
				System.out.println("Trocando Processo em Execução(" + interruptions + ")!!!");
				System.out.println("----------------------------------------------------- ");
			}
			vm.cpu.schedulerRunningClock = 0;
			if(vm.processManager.runningProcess != null) {
				vm.processManager.saveCpuContext();
				if(vm.processManager.runningProcess.processState == ProcessState.RUNNING) {
					vm.processManager.runningProcess.processState = ProcessState.READY;
					vm.processManager.setCurrentProcess();
				}
			}
			vm.processManager.schedulerProcess();
		}

		public void handleIO (Interruptions interruptions) {
			if (enableProcessMenagerWarnings) {
				System.out.println("Processo será finalizado(" + interruptions + ")!!!");
				System.out.println("----------------------------------------------------- ");
			}
			vm.processManager.finalizeConcorrenteIO();
		}

		public void defaultInterruptionCase (Interruptions interruptions) {
			System.out.println("VISHH... Uma interrupção aconteceu enquanto executavamos o programa!!! --> Interrupção: " + interruptions);
			System.out.println("----------------------------------------------------- ");
			vm.cpu.schedulerRunningClock = 0;
			vm.processManager.stopProcess();
		}
	}

	public class TrapHandler {
		public void trap(CPU cpu) {
			ProcessControlBlock currentProcessRunning = vm.processManager.getProcessRunning();
			vm.processManager.saveCpuContext(); // salva o estado do processo e bloqueia ele, apos isso escolhe um novo processo para rodar
			currentProcessRunning.processState = ProcessState.BLOCKED;
			vm.ioManager.addIO(currentProcessRunning, cpu.reg[8], cpu.reg[9]);
			vm.processManager.setBlockedProcess(currentProcessRunning); // adiciono o processo a lista de processos bloqueados
			vm.processManager.schedulerProcess(); // avisa o gerente de processos para rodar outro processo enquanto espera o IO finalizar
			vm.ioManager.ioSemaphore.release();
		}
	}

	public class MemoryManager {
		private Word[] memory;
		int freeFrames;
		int amountOfFrames;
		int assignedFrames = 0;
		private Boolean[] freeFrameMemoryMap;
	
		public MemoryManager(Word[] memory) {
			this.memory = memory;
			this.freeFrames = memory.length/pageLength;
			this.amountOfFrames = freeFrames;
			this.freeFrameMemoryMap = new Boolean[freeFrames];
			for(int i = 0; i<freeFrames; i++) {
				this.freeFrameMemoryMap[i] = false;
			}
		}
	
		// método utilizado para encontrar um frame livre, se encontrar algum que não esteja em usu, ele retorna a posição do frame, caso contrario, retorna -1 como erro
		private int findFreeFrame() {
			for(int i = 0; i < amountOfFrames; i++) {
				if(freeFrameMemoryMap[i] == false) {
					return i;
				}
			}
			return -1;
		}

		// método utilizado para alocar frames na memoria, se conseguir alocar ele retorna um array de inteiros com os frames, caso contrario, retorna null
		public int[] allocateFrame(Word[] w) {
			if (freeFrames==0) {
				System.out.println("ERRO ao Alocar Memória, Nenhum Frame Disponível!!!");
				return null;
			}
			if (w.length < 1) {
				System.out.println("ERRO ao Alocar Memória, Tamanho de Palavra Inválido, Operação Abortada!!!");
				return null;
			}
			int requestedFrames = (int)Math.ceil((double)w.length/(double)pageLength);      // calcula quantos frames serão necessários
			if (requestedFrames <= freeFrames) {                                            // verifica se temos a quantidade de frames necessárias				
				int[] frames = new int[requestedFrames];                                    // array utilizado para armazenar os frames da palavra	
				int lastAddedIdx = 0;                                                       // index utilizado para iterar os dados recebidos
				
				for(int currentFrame = 0; currentFrame < requestedFrames; currentFrame++) { // preenche os frames de acordo com o tamanho da palavra
					int frame = findFreeFrame();                                            // procura um frame livre	
					for(int offset = 0; offset < pageLength; offset++) { // preenche a posição da memória adequada com os dados
						if(lastAddedIdx + offset >= w.length) {
							break;
						}
						memory[frame * pageLength + offset].opc = w[lastAddedIdx + offset].opc;
						memory[frame * pageLength + offset].r1 = w[lastAddedIdx + offset].r1;
						memory[frame * pageLength + offset].r2 = w[lastAddedIdx + offset].r2;
						memory[frame * pageLength + offset].p = w[lastAddedIdx + offset].p;
					}
					lastAddedIdx = lastAddedIdx + pageLength;     // controla a iteração em cima de w
					freeFrameMemoryMap[frame] = true;             // marcamos o frame no mapa como ocupado
					frames[currentFrame] = frame;                 // guardamos o numero do frame
					freeFrames--;                                 // reduzimos a quantidade de frames disponiveis
					assignedFrames++;                             // aumentamos a quantidade de frames alocados
				}
				return frames; // array de frames a ser retornado
			} else {
				System.out.println("ERRO ao Alocar Memória, Não Existe a Quantidade de Frames Necessárias Livres!!!");
				return null;
			}	
		}
	
		public void freesFrames(int[] frames) {			
			for(int i = 0; i < frames.length; i++) {       // passa por todos os frames
				if(freeFrameMemoryMap[frames[i]] = true) { // verifica se a memória está alocada
					freeFrameMemoryMap[frames[i]] = false; // libera a memória
					freeFrames++;                          // aumentamos a quantidade de frames lives
					assignedFrames--;                      // reduzimos a quantidade de frames alocados
				}
			}
		}

		public void freeAllFrames(){
			this.freeFrames = this.memory.length/pageLength; // armazena novamente a quantidade de frames disponiveis
			this.assignedFrames = 0;                         // diz que nao tem nenhum frame sendo utilizado
			for(int i = 0; i<freeFrames; i++) {
				this.freeFrameMemoryMap[i] = false;          // para cada frame na memoria, define ele para nao ocupado
				for(int offset = 0; offset < pageLength; offset++) {
					memory[i * pageLength + offset].opc = Opcode.___;
					memory[i * pageLength + offset].r1  = -1;
					memory[i * pageLength + offset].r2  = -1;
					memory[i * pageLength + offset].p   = -1;
				}				
			}
		}
	}

	public class ProcessControlBlock {
		int uniqueId;
		int[] processMemoryPage;
		int pc;
		int[] reg;
		ProcessState processState;

		public Integer getProcessUniqueId () {
			return uniqueId;
		}
	
		public ProcessControlBlock(int uniqueId, int[] processMemoryPage) {
			this.uniqueId = uniqueId;
			this.processMemoryPage = processMemoryPage;
			this.pc = 0;
			this.reg = new int[10];
			this.processState = ProcessState.READY;
		}
	}

	public class ProcessManager {
		private ProcessControlBlock runningProcess;
		private int lastUniqueProcessId = 0;
		private CPU cpu;
		private MemoryManager memoryManager;
		private ArrayList<ProcessControlBlock> runningProcessList = new ArrayList<>();
		private ArrayList<ProcessControlBlock> blockedProcessList = new ArrayList<>();
	
		public ProcessManager(CPU cpu, MemoryManager memoryManager) {
			this.cpu = cpu;
			this.memoryManager = memoryManager;
		}
	
		// método utilizado para criar um processo na memória, esse método aloca o espaço necessário para a execução do programa. Se conseguir alocar ele retorna true, se não, false.
		public boolean createNewProcess(Word[] p) {
			int[] processMemoryPage = memoryManager.allocateFrame(p);
			if(processMemoryPage == null) {
				System.out.println("Método de criação de processo falhou. MOTIVO: páginas de memória indisponível");	
				return false;
			}
			ProcessControlBlock newProgram = new ProcessControlBlock(lastUniqueProcessId, processMemoryPage);
			runningProcessList.add(newProgram);
			lastUniqueProcessId++;
			schedulerProcess();
			return true;
		}
	
		// método utilizado para rodar os programas da lista de processos, esse método roda os programas em ordem de criação!!
		public boolean runProcess() {
			if (!runningProcessList.isEmpty()){
				runningProcess = runningProcessList.get(0);
				if (enableProcessMenagerWarnings) {
					System.out.println("Programa número " + (runningProcess.getProcessUniqueId()+1) + " está sendo executado");
				}
			}
			cpu.start();
			schedulerProcess();
			return true;
		}
		
		// método utilizado para rodar um programa, funcionalidade similar ao de cima, porém recebe o identificador uníco de um processo como parâmetro e roda esse especifico programa.
		public boolean runProcessWithId(int uniqueId) {
			ProcessControlBlock currentRunningProcess;
			try {
				currentRunningProcess = runningProcessList.get(uniqueId);
			} catch(IndexOutOfBoundsException e){
				return false;
			}
			cpu.setContext(0, cpu.minMemoryBorder, cpu.maxMemoryBorder, currentRunningProcess.processMemoryPage);
			cpu.start();
			return true;
		}

		// método utilizado para matar um processo, esse programa espera receber um idenficador uníco do processo para finalizar ele, se ele conseguir encontrar, o programa retorna true, se não, false.
		// ao matar um processo, a memória antes utilizada por ele é marcada como livre, sendo assim, os dados continuam carregados, mas se algum programa precisar ele vai poder alocar aquela parte da memoria.
		public boolean killProcessWithId(int uniqueId) {
			ProcessControlBlock currentRunningProcess;
			try {
				currentRunningProcess = runningProcessList.get(uniqueId);
			} catch(IndexOutOfBoundsException e){
				return false;
			}
			memoryManager.freesFrames(currentRunningProcess.processMemoryPage);
			runningProcessList.remove(currentRunningProcess);
			return true;
		}

		// método utilizado para matar todos os processos da lista
		public boolean killAllProcess() {
			boolean isKilled = false;
			boolean hasFalse = false;
			for (int i = 0; i < runningProcessList.size(); i++) {
				isKilled = killProcessWithId(runningProcessList.get(i).getProcessUniqueId());
				if (!isKilled) {
					hasFalse = true;
				}
			}
			if (hasFalse) {
				isKilled = false;
			}
			return isKilled;
		}

		// método utilizado para matar todos os processos da lista, mesmo se eles já estiverem finalizados
		public boolean killAllProcessEvenFinished() {
			memoryManager.freeAllFrames();
			return true;
		}
	
		// método utiolizado para gerenciar os processos, esse método executa a troca de processo executado entre um intervalo de tempo X, definido na CPU do sistema.
		public void schedulerProcess() {
			if(!runningProcessList.isEmpty()) {
				if (runningProcessList.get(0).processState == ProcessState.READY) {
					runningProcess = runningProcessList.get(0);
					runningProcessList.remove(runningProcess);
					if (enableProcessMenagerWarnings) {
						System.out.println("Programa número " + (runningProcess.getProcessUniqueId()+1) + " está sendo executado");
					}
					cpu.pc = runningProcess.pc;
					cpu.reg = runningProcess.reg;
					cpu.runningPageTable = runningProcess.processMemoryPage;
					cpu.interruptions = Interruptions.interruptionNone;
					vm.cpu.isCpuAtiva = true;
					runningProcess.processState = ProcessState.RUNNING;
					vm.cpu.cpuSemaphore.release();
				} else { 
					ProcessControlBlock auxProcess = runningProcessList.get(0);
					runningProcessList.remove(0);
					runningProcessList.add(auxProcess);
				}
			}
		}

		public ProcessControlBlock getProcessRunning() {
			return runningProcess;
		}

		public void setCurrentProcess() {
			runningProcessList.add(runningProcess);
		}

		public void setBlockedProcess(ProcessControlBlock process) {
			blockedProcessList.add(process);
		}

		public void saveCpuContext() {
			runningProcess.reg = cpu.reg;
			runningProcess.pc = cpu.pc;
		}

		public void stopProcess() {
			vm.cpu.isCpuAtiva = false;
			runningProcess.processState = ProcessState.FINISHED;
			System.out.println("Programa número " + (runningProcess.getProcessUniqueId()+1) + " foi finalizado.");
			schedulerProcess();
		}

		public ProcessControlBlock removeBlockedProcess(ProcessControlBlock process) {
			ProcessControlBlock processControlBlock = blockedProcessList.get(0);
			blockedProcessList.remove(processControlBlock);
			return processControlBlock;
		}

		public void finalizeConcorrenteIO() {
			ProcessControlBlock processControlBlock = blockedProcessList.get(0);
			blockedProcessList.remove(processControlBlock);
			processControlBlock.processState = ProcessState.READY;
			runningProcessList.add(processControlBlock);
			schedulerProcess();
		}
	}

	public class IoMenager extends Thread {
		public ArrayList<Integer> menuOption = new ArrayList<Integer>();
		private ArrayList<IoRequest> ioRequestList = new ArrayList<IoRequest>();
		private Word[] memory;
		private Semaphore ioSemaphore = new Semaphore(0);

		public IoMenager(Word[] _memory) {
			this.memory = _memory;
		}

		public class IoRequest {
			ProcessControlBlock process;
			int reg8;
			int reg9;

			public IoRequest(ProcessControlBlock process, int reg8) {
				this.process = process;
				this.reg8 = reg8;
			}

			public IoRequest(ProcessControlBlock process, int reg8, int reg9) {
				this.process = process;
				this.reg8 = reg8;
				this.reg9 = reg9;
			}
		}

		public int translateToPhysicalMemoryAddress(ProcessControlBlock someProcess, int logicMemoryAddress) { // método desenvolvido conforme recomendação do PDF do enunciado da fase 4
			int page = logicMemoryAddress/Sistema.pageLength;   // tópico 1.4 utilizado como referência
			int offset = logicMemoryAddress%Sistema.pageLength;
			int physicalMemoryAddress;
			try {
				physicalMemoryAddress = someProcess.processMemoryPage[page]*Sistema.pageLength+offset;
			} catch (IndexOutOfBoundsException e) {
				vm.cpu.interruptions = Interruptions.interruptionInvalidPageAccessed;
				return -1;
			}
			return physicalMemoryAddress;
		}

		public void run() {
			while (true) {
				try {
					ioSemaphore.acquire();
				} catch (Exception e) {
					System.out.println("!!! Semaphoro sendo utilizado, ocorreu um erro ao tentar dar acquire !!!"); // caso ocorra algum erro no acquire ele exibe a mensagem
				}
				IoRequest currentIORequest = ioRequestList.get(0);
				ioRequestList.remove(0);
				switch (currentIORequest.reg8) { // o registrador 8 armazena a chamada de sistema, o valor que define o que deve ser feito
					case 1: // trap input
						System.out.println("Programa número " + (currentIORequest.process.getProcessUniqueId()+1) + " está executando.");
						System.out.println(" --> Por favor digite um valor, apenas inteiros!!! ");
						Scanner input = new Scanner(System.in);
						int nextInt = input.nextInt();
						memory[translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9)].p = nextInt; // armazena o valor digitado
						memory[translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9)].opc = Opcode.DATA; // armazena o valor no DATA
						System.out.println(" --> INPUT: Valor armazenado na posição: " + translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9) + " --> Valor armazenado: "); // exibe o valor armazenado
						Aux.dump(memory[translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9)]); // exibe o dump de memória com o valor armazenado no registrador 9, salvo como DATA
						System.out.printf("\n");
						interruptionHandler.handle(Interruptions.interruptionConcorrenteIO);
						break;

					case 2: // trap output
						System.out.println("Programa número " + (currentIORequest.process.getProcessUniqueId()+1) + " está executando.");
						System.out.println(" --> OUTPUT: Valor armazenado na posição: " + translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9) + " --> Valor armazenado: ");
						Aux.dump(memory[translateToPhysicalMemoryAddress(currentIORequest.process, currentIORequest.reg9)]);
						System.out.printf("\n");
						interruptionHandler.handle(Interruptions.interruptionConcorrenteIO);
						break;

					case -1: // menu
						System.out.println("\n");
						System.out.println("\\---------------------------------- Menu do Sistema ----------------------------------/");
						System.out.println("\n");
						System.out.println("Opção 1: Opção vazia, deixar o programa em pausa");
						System.out.println("Opção 2: Rodar a CPU (rode a CPU apenas uma vez, RODE ANTES DE ADICIONAR ALGUM PROGRAMA)");
						System.out.println("Opção 3: Realizar o dump de memória");
						System.out.println("Opção 4: Programa de teste Fatorial");
						System.out.println("Opção 5: Programa de teste TRAP INPUT");
						System.out.println("Opção 6: Programa de teste TRAP OUTPUT");
						System.out.println("Opção 7: Programa de teste overflow");
						System.out.println("Opção 8: Matar os processos e liberar espaço na memória");
						System.out.println("Opção 0: SAIR");
						System.out.println("\n");
						System.out.println("Escolha alguma das opções descritas acima:");
						Scanner input2 = new Scanner(System.in);
						int anInt2 = input2.nextInt();
						System.out.println("/-------------------------------------------------------------------------------------\\");
						System.out.println("\n");
						menuOption.add(anInt2);
						vm.menuSemaphore.release();
						break;

					default:
						System.out.println("\n");
						System.out.println("*************************************************************");
						System.out.println("Opção invalida do Gerenciador de IO, escolha uma opção válida");
						System.out.println("*************************************************************");
						System.out.println("\n");
						break;
				}
			}
		}

		public void addIO(ProcessControlBlock processControlBlock, int reg8, int reg9) {
			ioRequestList.add(new IoRequest(processControlBlock, reg8, reg9));
		}

		public void addIO(ProcessControlBlock processControlBlock, int reg8) {
			ioRequestList.add(new IoRequest(processControlBlock, reg8));
		}
	}
	// -------------------------------------------------------------------------------------------------------
    // -------------------  S I S T E M A --------------------------------------------------------------------

	public VM vm;
	public InterruptionsHandler interruptionHandler;
	public TrapHandler trapHandler;

    public Sistema(){   						           // a VM com tratamento de interrupções
		 interruptionHandler = new InterruptionsHandler();  // atribui uma nova instancia do manipulador de interrupções
		 trapHandler = new TrapHandler();                   // atribui uma nova instancia do manipulador de traps de chamada de sistema
		 vm = new VM(interruptionHandler, trapHandler);     // cria a VM com o interruptionHandler e o trapHandler criados anteriormente
	}

    // -------------------  S I S T E M A - fim --------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

	
    // -------------------------------------------------------------------------------------------------------
    // ------------------- instancia e testa sistema
	public static void main(String args[]) {
		//cria uma nova instancia do sistema
		Sistema s = new Sistema();
		//ajusta a flag para o gerenciador de processos exibir mensagens ou nao
		enableProcessMenagerWarnings = false;

		//test1 - programa que testa fibonnaci		
		//s.test1();
		//test2 - programa que testa progminimo
		//s.test2();
		//teste3 - programa que testa fatorial
		//s.test3();
		//test4 - programa que testa interrupções de endereço invalido
		//s.test4();
		//teste5 - programa que testa manipulador de chamada de sistema(trap 1 - input)
		//s.test5();
		//teste6 - programa que testa manipulador de chamada de sistema(trap 2 - output)
		//s.test6();
		//test7 - programa que testa interrupções de intrução invalida
		//s.test7();
		//teste8 - programa que testa interrupções de overflow de operações matematicas
		//s.test8();
		//teste9 - Programa que testa o gerenciador de memória, esse programa apenas carrega a memória com dados "inuteis"
		//s.test9();
		//test10 - programa que testa o gerenciador de processos, esse programa carrega cinco fatoriais diferentes
		//s.test10();
		//test11 - programa que testa o gerenciador de processos caso ocorra alguma interrupção que quebre o sistema
		//s.test11();

		s.menu();
	}
    // -------------------------------------------------------------------------------------------------------
    // --------------- TUDO ABAIXO DE MAIN É AUXILIAR PARA FUNCIONAMENTO DO SISTEMA - nao faz parte 

	// -------------------------------------------- teste do sistema ,  veja classe de programas
	
	// Programa que testa o programa fibonacci
	public void test1() {
		// Aux aux = new Aux();
		Word[] p = new Programas().fibonacci10;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 33);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 33);
	}

	// Programa que testa o programa progMinimo
	public void test2(){
		// Aux aux = new Aux();
		Word[] p = new Programas().progMinimo;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 15);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 15);
	}

	// Programa que testa o programa fatorial
	public void test3(){
		// Aux aux = new Aux();
		Word[] p = new Programas().fatorial;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 15);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 15);
	}

	// Programa que testa o programa testExercicio
	public void test4() {
		// Aux aux = new Aux();
		Word[] p = new Programas().testExercicio;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 30);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, vm.tamMem);
	}

	// Programa que testa o programa testTrapInput
	public void test5() {
		// Aux aux = new Aux();
		Word[] p = new Programas().testTrapHandlerInput;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 10);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 10);
	}

	// Programa que testa o programa testTrapOut
	public void test6() {
		// Aux aux = new Aux();
		Word[] p = new Programas().testTrapHandlerOutput;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 10);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 10);
	}

	// Programa que testa o programa testInstructionsInvalid
	public void test7() {
		// Aux aux = new Aux();
		Word[] p = new Programas().testInvalidInstructions;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 10);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 10);
	}

	// Programa que testa o programa testInstructionsOverflow
	public void test8() {
		// Aux aux = new Aux();
		Word[] p = new Programas().testInstructionsOverflow;
		vm.processManager.createNewProcess(p);
		Aux.dumpBefore(vm.m, 0, 10);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 10);
	}

	// Programa que testa o programa dummyProgramForMemoryTest - passamos valores para esse programa para mostrar que a memória foi populada com diferentes programas
	public void test9() {
		// Aux aux = new Aux();
		Word[] p; // palavra responsavel por carregar o programa
		ArrayList<Boolean> programStatusList = new ArrayList<>(); // array que contem os status de carregamento dos programas
		
		Aux.dumpBefore(vm.m, 0, 128);

		p = new Programas().dummyProgramForMemoryTest(1);
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().dummyProgramForMemoryTest(2);
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().dummyProgramForMemoryTest(3);
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().dummyProgramForMemoryTest(4);
		programStatusList.add(vm.processManager.createNewProcess(p));
		Aux.dumpProcessStatus(programStatusList);
		Aux.dumpAfter(vm.m, 0, 128);
	}
	
	// Programa que testa o programa de fatorial, esse teste passa diferentes valores ao fatorial para testar o gerenciador de processos e mostrar que vários programas diferentes rodaram
	public void test10() {
		// Aux aux = new Aux();
		Word[] p; // palavra responsavel por carregar o programa
		ArrayList<Boolean> programStatusList = new ArrayList<>(); // array que contem os status de carregamento dos programas

		p = new Programas().fatorialWithValue(6); // criando instancia do programa fatorial de 6
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().fatorialWithValue(5); // criando instancia do programa fatorial de 5
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().fatorialWithValue(4); // criando instancia do programa fatorial de 4
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().fatorialWithValue(3); // criando instancia do programa fatorial de 3
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().fatorialWithValue(2); // criando instancia do programa fatorial de 2
		programStatusList.add(vm.processManager.createNewProcess(p));

		Aux.dumpProcessStatus(programStatusList);

		Aux.dumpBefore(vm.m, 0, 80);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 80);
	}

	// Programa que testa vários programas que geram interrupções que quebram o sistema, nesse caso, o gerenciador de processos lida com isso, quando ocorre algum problema ele finaliza o processo em execução
	public void test11() {
		// Aux aux = new Aux();
		Word[] p; // palavra responsavel por carregar o programa
		ArrayList<Boolean> programStatusList = new ArrayList<>(); // array que contem os status de carregamento dos programas

		p = new Programas().testExercicio; // criando instancia do programa que testa instrução inválida
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().testInvalidInstructions; // criando instancia do programa que testa instrução inválida
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().testInstructionsOverflow; // criando instancia do programa que testa overflow
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().testTrapHandlerInput; // criando instancia do programa que testa chamada de sistema (input)
		programStatusList.add(vm.processManager.createNewProcess(p));
		p = new Programas().testTrapHandlerOutput; // criando instancia do programa que testa chamada de sistema (output)
		programStatusList.add(vm.processManager.createNewProcess(p));

		Aux.dumpProcessStatus(programStatusList);

		Aux.dumpBefore(vm.m, 0, 128);
		vm.processManager.runProcess();
		Aux.dumpAfter(vm.m, 0, 128);
	}

	public void menu() {
		Word[] p;
		boolean run = true;
		ArrayList<Boolean> programStatusList = new ArrayList<>();
		
		new Thread(() -> {
			vm.ioManager.start(); // Inicia o gerente de IO
		}).start();

		while(run) {
			vm.ioManager.addIO(null, -1); // mostra o menu ao usuario e espera ele digitar um valor valido de input
			vm.ioManager.ioSemaphore.release();
			try {
				vm.menuSemaphore.acquire();
			} catch (Exception e) {
				System.out.println("!!! Semaphoro sendo utilizado, ocorreu um erro ao tentar dar acquire !!!"); // caso ocorra algum erro no acquire ele exibe a mensagem
			}
			int selection = vm.ioManager.menuOption.get(0);
			vm.ioManager.menuOption.remove(0);
			switch(selection) {
				case 1:
					System.out.println("\n");
					System.out.println("Sistema está em pausa");
					System.out.println("\n");
					break;
				case 2:
					vm.processManager.runProcess();
					break;
				case 3:
					Aux.dumpMemoria(vm.m, 0, 128);
					break;
				case 4:
					System.out.println("\n");
					programStatusList.clear();
					System.out.println(" --> De qual numero você deseja saber o fatorial? (DIGITE APENAS NUMEROS INTEIROS): ");
					Scanner scanner = new Scanner(System.in);
					int value = scanner.nextInt();
					p = new Programas().fatorialWithValue(value); // criando instancia do programa fatorial com o numero digitado pelo user
					programStatusList.add(vm.processManager.createNewProcess(p));
					Aux.dumpProcessStatus(programStatusList);
					break;
				case 5:
					System.out.println("\n");
					programStatusList.clear();
					p = new Programas().testTrapHandlerInput;
					programStatusList.add(vm.processManager.createNewProcess(p));
					Aux.dumpProcessStatus(programStatusList);
					break;
				case 6:
					System.out.println("\n");
					programStatusList.clear();
					p = new Programas().testTrapHandlerOutput;
					programStatusList.add(vm.processManager.createNewProcess(p));
					Aux.dumpProcessStatus(programStatusList);
					break;
				case 7:
					System.out.println("\n");
					programStatusList.clear();
					p = new Programas().testInstructionsOverflow;
					programStatusList.add(vm.processManager.createNewProcess(p));
					Aux.dumpProcessStatus(programStatusList);
					break;
				case 8:
					vm.processManager.killAllProcessEvenFinished();
					System.out.println("\n");
					System.out.println("******************");
					System.out.println("Processos finalizados e memória liberada com sucesso!!!");
					System.out.println("******************");
					break;
				case 0:
					run = false;
					break;
				default:
					System.out.println("\n");
					System.out.println("******************");
					System.out.println("Opção invalida do Gerenciador de IO, escolha uma opção válida");
					System.out.println("******************");
					System.out.println("\n");
					break;
			}
		}

		vm.cpu.stop();
		vm.ioManager.stop();
	}

	// -------------------------------------------  classes e funcoes auxiliares
    public static class Aux {
		// método utilizado para formatar o dump da memória
		public static void dump(Word w) {
			System.out.print(" [ "); 
			System.out.print(w.opc); System.out.print(", ");
			System.out.print(w.r1);  System.out.print(", ");
			System.out.print(w.r2);  System.out.print(", ");
			System.out.print(w.p);  System.out.println("  ] ");
		}

		// método utilizado para realizar o dump de memoria da execução do sistema
		public static void dumpMemoria(Word[] m, int ini, int fim) {
			System.out.println("---------------------------------------------------------------------------------------");
			System.out.println("Dump de memória com os programas no sistema: ");
			for (int i = ini; i < fim; i++) {
				if (i < 10) {
					System.out.print(i); System.out.print(":   ");  dump(m[i]);
				} else if (i < 100) {
					System.out.print(i); System.out.print(":  ");  dump(m[i]);
				} else {
					System.out.print(i); System.out.print(": ");  dump(m[i]);
				}		
			}
			if (enableProcessMenagerWarnings) {
				System.out.println("---------------------------------------------------------------------------------------");
			}
		}

		// método utilizado para realizar o dump de memoria antes a execução do sistema
		public static void dumpBefore(Word[] m, int ini, int fim) {
			System.out.println("---------------------------------------------------------------------------------------");
			System.out.println("Dump de memória antes de executar todos os programas: ");
			for (int i = ini; i < fim; i++) {
				if (i < 10) {
					System.out.print(i); System.out.print(":   ");  dump(m[i]);
				} else if (i < 100) {
					System.out.print(i); System.out.print(":  ");  dump(m[i]);
				} else {
					System.out.print(i); System.out.print(": ");  dump(m[i]);
				}		
			}
			if (enableProcessMenagerWarnings) {
				System.out.println("---------------------------------------------------------------------------------------");
			}
		}

		// método utilizado para realizar o dump de memoria após a execução do sistema
		public static void dumpAfter(Word[] m, int ini, int fim) {
			System.out.println("---------------------------------------------------------------------------------------");
			System.out.println("Dump de memória depois de executar todos os programas: ");
			for (int i = ini; i < fim; i++) {
				if (i < 10) {
					System.out.print(i); System.out.print(":   ");  dump(m[i]);
				} else if (i < 100) {
					System.out.print(i); System.out.print(":  ");  dump(m[i]);
				} else {
					System.out.print(i); System.out.print(": ");  dump(m[i]);
				}		
			}
			if (enableProcessMenagerWarnings) {
				System.out.println("---------------------------------------------------------------------------------------");
			}
		}

		// esse metodo realiza a impressão do status de cada um dos programas carregados no scheduler
		public static void dumpProcessStatus(ArrayList<Boolean> programStatusList) {
			System.out.println("\n");
			System.out.println("---------------------------------------------------------------------------------------");
			for (int i = 0; i < programStatusList.size(); i++) {
				System.out.println(" * Processo adicionado a memória com sucesso: " + programStatusList.get(i));
			}
			System.out.println("---------------------------------------------------------------------------------------");
			System.out.println("\n");	
		}

		// método antigo utilizado para carregar o programa na cpu
		public static void carga(Word[] p, Word[] m) {
			for (int i = 0; i < p.length; i++) {
				m[i].opc = p[i].opc;     m[i].r1 = p[i].r1;     m[i].r2 = p[i].r2;     m[i].p = p[i].p;
			}
		}
	}
    // -------------------------------------------  fim classes e funcoes auxiliares
	
    //  -------------------------------------------- programas aa disposicao para copiar na memoria (vide aux.carga)
    public class Programas {
	    public Word[] progMinimo = new Word[] {
		    //       OPCODE      R1  R2  P         :: VEJA AS COLUNAS VERMELHAS DA TABELA DE DEFINICAO DE OPERACOES
			//                                     :: -1 SIGNIFICA QUE O PARAMETRO NAO EXISTE PARA A OPERACAO DEFINIDA
		    new Word(Opcode.LDI, 0, -1, 999), 		
			new Word(Opcode.STD, 0, -1, 10), 
			new Word(Opcode.STD, 0, -1, 11), 
			new Word(Opcode.STD, 0, -1, 12), 
			new Word(Opcode.STD, 0, -1, 13), 
			new Word(Opcode.STD, 0, -1, 14), 
			new Word(Opcode.STOP, -1, -1, -1) 
		};

	    public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
			new Word(Opcode.LDI, 1, -1, 0), 
			new Word(Opcode.STD, 1, -1, 20),    // 20 posicao de memoria onde inicia a serie de fibonacci gerada  
			new Word(Opcode.LDI, 2, -1, 1),
			new Word(Opcode.STD, 2, -1, 21),      
			new Word(Opcode.LDI, 0, -1, 22),       
			new Word(Opcode.LDI, 6, -1, 6),
			new Word(Opcode.LDI, 7, -1, 30),       
			new Word(Opcode.LDI, 3, -1, 0), 
			new Word(Opcode.ADD, 3, 1, -1),
			new Word(Opcode.LDI, 1, -1, 0), 
			new Word(Opcode.ADD, 1, 2, -1), 
			new Word(Opcode.ADD, 2, 3, -1),
			new Word(Opcode.STX, 0, 2, -1), 
			new Word(Opcode.ADDI, 0, -1, 1), 
			new Word(Opcode.SUB, 7, 0, -1),
			new Word(Opcode.JMPIG, 6, 7, -1), 
			new Word(Opcode.STOP, -1, -1, -1),   // POS 16
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),   // POS 20
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),  // ate aqui - serie de fibonacci ficara armazenada
		};   

		public Word[] fatorial = new Word[] { 	 // este fatorial so aceita valores positivos.   nao pode ser zero
												 // linha   coment
			new Word(Opcode.LDI, 0, -1, 6),      // 0   	r0 é valor a calcular fatorial
			new Word(Opcode.LDI, 1, -1, 1),      // 1   	r1 é 1 para multiplicar (por r0)
			new Word(Opcode.LDI, 6, -1, 1),      // 2   	r6 é 1 para ser o decremento
			new Word(Opcode.LDI, 7, -1, 8),      // 3   	r7 tem posicao de stop do programa = 8
			new Word(Opcode.JMPIE, 7, 0, 0),     // 4   	se r0=0 pula para r7(=8)
			new Word(Opcode.MULT, 1, 0, -1),     // 5   	r1 = r1 * r0
			new Word(Opcode.SUB, 0, 6, -1),      // 6   	decrementa r0 1 
			new Word(Opcode.JMP, -1, -1, 4),     // 7   	vai p posicao 4
			new Word(Opcode.STD, 1, -1, 10),     // 8   	coloca valor de r1 na posição 10
			new Word(Opcode.STOP, -1, -1, -1),    // 9   	stop
			new Word(Opcode.DATA, -1, -1, -1) 
		};  // 10   ao final o valor do fatorial estará na posição 10 da memória         

		public Word[] fatorialWithValue(int value) {
			Word[] fatorial = new Word[] {            // este fatorial so aceita valores positivos.   nao pode ser zero
				new Word(Opcode.LDI, 0, -1, value),  // 0   	r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1),      // 1   	r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1),      // 2   	r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8),      // 3   	r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0),     // 4   	se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1),     // 5   	r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1),      // 6   	decrementa r0 1 
				new Word(Opcode.JMP, -1, -1, 4),     // 7   	vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10),     // 8   	coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1),   // 9   	stop
				new Word(Opcode.DATA, -1, -1, -1)    // 10      ao final o valor do fatorial estará na posição 10 da memória     
			};

			return fatorial;
		};
		
		 // programa desenvolvido na tentativa de resolver um exercicio, no fim o programa serviu para demonstrar um endereço invalido
		public Word[] testExercicio = new Word[] {
			new Word(Opcode.LDI, 1, -1, 50),
			new Word(Opcode.LDI, 7, -1, 7),
			new Word(Opcode.JMPIG, 7, 1, -1),
			new Word(Opcode.LDI, 7, -1, 69),
			new Word(Opcode.STD, 7, -1, 60),
			new Word(Opcode.STOP, 1, -1, 0),
			new Word(Opcode.LDI, 2, -1, 0),
			new Word(Opcode.ADD, 2, 1, -1),
			new Word(Opcode.LDI, 6, -1, 1),
			new Word(Opcode.SUB, 1, 6, -1),
			new Word(Opcode.LDI, 7, -1, 8),
			new Word(Opcode.JMPIG, 7, 1, -1),
			new Word(Opcode.STD, 0, -1, 50),
			new Word(Opcode.STD, 1, -1, 51),
			new Word(Opcode.STD, 2, -1, 52),
			new Word(Opcode.STD, 3, -1, 53),
			new Word(Opcode.STD, 4, -1, 54),
			new Word(Opcode.STD, 5, -1, 55),
			new Word(Opcode.STD, 6, -1, 56),
			new Word(Opcode.STD, 7, -1, 57),
			new Word(Opcode.LDI, 1, -1, 59), // endereço invalido - interruptionInvalidAddress
			new Word(Opcode.STD, 1, -1, 1024),
			new Word(Opcode.STOP, 1, -1, 0)
		};

		// programa para executar teste de chamada de sistema(leitura de um inteiro)
		public Word[] testTrapHandlerInput = new Word[] { 
			new Word(Opcode.LDI, 8, -1, 1), 
			new Word(Opcode.LDI, 9, -1, 8),
			new Word(Opcode.TRAP, -1, -1, -1),
			new Word(Opcode.STOP, 1, -1, 0)
		};

		// programa para executar teste de saida de sistema(output)
		public Word[] testTrapHandlerOutput = new Word[] { 
			new Word(Opcode.LDI, 8, -1, 2),    // registrador setado para o manipulador de chamada de sistema pegar o output
			new Word(Opcode.LDI, 9, -1, 8),    // registrador seta o output para o item armazenado no endereço 59
			new Word(Opcode.STD, 9, -1, 8),    // guarda o item armazenado no registrador r9(8) no endereço 59
			new Word(Opcode.TRAP, -1, -1, -1), // ocorre uma chamada de sistema, o valor de saida(output) precisa ser DATA 8 no endereço 8
			new Word(Opcode.STOP, 1, -1, 0)
		};

		// programa para teste de chamada de sistema, leitura
		public Word[] testInvalidInstructions = new Word[] { 
			new Word(Opcode.___, 8, -1, 1), 
			new Word(Opcode.LDI, 9, -1, 50),
			new Word(Opcode.TRAP, -1, -1, -1),
			new Word(Opcode.STOP, 1, -1, 0),
			new Word(Opcode.DATA, 50, -1, 1)
		};

		// programa para teste da interrupção de overflow, quando ocorre um overflow em uma operação matematica
		public Word[] testInstructionsOverflow = new Word[] { 
			new Word(Opcode.LDI, 0, -1, 2147483647),
			new Word(Opcode.LDI, 1, -1, 1236),
			new Word(Opcode.ADD, 0, 1, -1),
			new Word(Opcode.STD, 0, -1, 8),
			new Word(Opcode.STD, 1, -1, 9),
			new Word(Opcode.STOP, 1, -1, 0),
			new Word(Opcode.DATA, 50, -1, 1)
		};

		public Word[] dummyProgramForMemoryTest(int value) { // programa de teste utilizado para preencher a memória do sistema com diferentes valores
			Word[] dummyData = new Word[] {
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value),
				new Word(Opcode.DATA, value, value, value)
			};

			return dummyData;
		};
    }
}
