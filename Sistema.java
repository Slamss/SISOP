// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Integrantes do Grupo: Edson Ricardo da Costa, Jonatas Van Groll Lemos e Lourenço Souza.
//
// Fase 1 - máquina virtual
// Fase 2 - interrupções
// Fase 3 - chamadas de sistema


import java.util.*;

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
		interruptionNone;                // interrupção padrão, ocorre quando não há interrupção
	}

	public class CPU {
									// característica do processador: contexto da CPU ...
		private int pc; 			// ... composto de program counter,
		private Word ir; 			// instruction register,
		private int[] reg;       	// registradores da CPU

		private Interruptions interruptions;              // CPU instancia as interrupções 
		private InterruptionsHandler interruptionHandler; // CPU instancia o manipulador de exceções
		private TrapHandler trapHandler;                  // CPU instancia o manipulador de traps para chamadas de sistema

		private int maxMemoryBorder; // armazena o valor máximo da borda da memória
		private int minMemoryBorder; // armazena o valor mínimo da borda da memória

		private Word[] m;   // CPU acessa MEMORIA, guarda referencia 'm' a ela. memoria nao muda. ee sempre a mesma.
			
		//private Aux aux = new Aux();

		public CPU(Word[] _m, InterruptionsHandler interruptionHandler, TrapHandler trapHandler) {  // referencia a MEMORIA, interruptions handler e trap handler passada na criacao da CPU
			m = _m; 				                     // usa o atributo 'm' para acessar a memoria
			reg = new int[10]; 		                     // aloca o espaço para os registradores
			this.interruptionHandler = interruptionHandler;  // associa o manipulador de exceções
			this.trapHandler = trapHandler;                  // associa o manipulador de de trap para chamadas de sistema
		}

		public void setContext(int _pc, int minMemoryBorder, int maxMemoryBorder) {  // no futuro esta funcao vai ter que ser
			pc = _pc;                                         // limite e pc (deve ser zero nesta versao)
			interruptions = Interruptions.interruptionNone;   // ajusta o interruptions para NONE
			this.minMemoryBorder = minMemoryBorder;               // ajusta o valor da borda mínima da memóia
			this.maxMemoryBorder = maxMemoryBorder;               // ajusta o valor da borda máxima da memória		
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

		public void run() { 		// execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente setado
			while (true) { 			// ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// FETCH
				if (isMemoryAddressValid(pc)) {  // verifica se o valor de memória é válido
					ir = m[pc]; 	// busca posicao da memoria apontada por pc, guarda em ir
					//if debug
					//showState();
				    // EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // para cada opcode, sua execução
						case LDI: // Rd ← k
							reg[ir.r1] = ir.p;
							pc++;
							break;

						case STD: // [A] ← Rs
							if (!isMemoryAddressValid(ir.p)) { 
								interruptions = Interruptions.interruptionInvalidAddress; // precisamos tratar antes pois de executar, pois se deixar para depois o java irá para o programa por causa da exceção do próprio java
								break;
							}
							m[ir.p].opc = Opcode.DATA;
							m[ir.p].p = reg[ir.r1];
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
							if (!isMemoryAddressValid(reg[ir.r1])) {
								interruptions = Interruptions.interruptionInvalidAddress; // precisamos tratar antes pois de executar, pois se deixar para depois o java irá para o programa por causa da exceção do próprio java
							}
							m[reg[ir.r1]].opc = Opcode.DATA;      
							m[reg[ir.r1]].p = reg[ir.r2];          
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
							if (!isMemoryAddressValid(ir.p)){ 
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
							trapHandler.trap(this);
							pc++;
							break;

						default:
							interruptions = Interruptions.interruptionInvalidInstruction;
							break;
					}
				}
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (!(interruptions == Interruptions.interruptionNone)) {
					interruptionHandler.handle(interruptions); // pega a interruption armazenada e manda ela para o manipulador de interrupções
					break; 									   // break sai do loop da cpu
				}
			}
		}
	}
    // ------------------ C P U - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------	
    // ------------------- V M  - constituida de CPU e MEMORIA -----------------------------------------------
    // -------------------------- atributos e construcao da VM -----------------------------------------------
	public class VM {
		public int tamMem;    
        public Word[] m;     
        public CPU cpu;    

        public VM(InterruptionsHandler interruptionHandler, TrapHandler trapHandler) {   // vm deve ser configurada com endereço de tratamento de interrupcoes
			// memória
  		 	 tamMem = 1024;
			 m = new Word[tamMem]; // m ee a memoria
			 for (int i=0; i<tamMem; i++) { m[i] = new Word(Opcode.___,-1,-1,-1); };
	  	 	 // cpu
			 cpu = new CPU(m, interruptionHandler, trapHandler);
	    }	
	}
    // ------------------- V M  - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
    // --------------------H A R D W A R E - fim -------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio ----------------------------------------------------------
	public class InterruptionsHandler {
		public void handle(Interruptions interruptions) {
			System.out.println("VISHH... Uma interrupção aconteceu enquanto executavamos o programa!!! --> Interrupção: " + interruptions);
			System.out.println("----------------------------------------------------- ");
		}
	}

	public class TrapHandler {
		Aux aux = new Aux();

		public void trap(CPU cpu) {
			System.out.println("Opa... Uma chamada de sistema ocorreu!!! --> " + " | " + cpu.reg[8] + " | " + cpu.reg[9] + " | ");

			switch (cpu.reg[8]) { // verificamos o que está armazenado no registrador 8, pois é nele que temos armazenado o que precisa ser feito na chamada do sistema
				case 1: // caso o valor seja 1, nós armazenamos o valor do inserido no input no registrador 9
					System.out.println(" --> Por favor digite um valor, apenas inteiros!!! ");
					Scanner keyboardInput = new Scanner(System.in);
					int keyboardValue = keyboardInput.nextInt();
					cpu.m[cpu.reg[9]].p = keyboardValue; // armazena o valor digitado
					cpu.m[cpu.reg[9]].opc = Opcode.DATA; // armazena o destimo como DATA
					System.out.printf(" --> Valor armazenado na posição: " + cpu.reg[9] + " --> Valor armazenado: "); // exibe o valor armazenado
					aux.dump(cpu.m[cpu.reg[9]]); // realoca a memoria da VM ao endereço de memória que foi armazenado no registrador 9
					break;
				case 2: // caso o valor seja 2, nós exibimos o dado armazenado no registrador 9
					System.out.printf(" --> Output do sistema: ");
					aux.dump(cpu.m[cpu.reg[9]]);
					break;
			}
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

		//test1 - programa que testa fibonnaci		
		//s.test1();

		//test2 - programa que testa progminimo
		//s.test2();

		//teste3 - programa que testa fatorial
		s.test3();

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
	}
    // -------------------------------------------------------------------------------------------------------
    // --------------- TUDO ABAIXO DE MAIN É AUXILIAR PARA FUNCIONAMENTO DO SISTEMA - nao faz parte 

	// -------------------------------------------- teste do sistema ,  veja classe de programas
	
	// Programa que testa o programa fibonacci
	public void test1() {
		Aux aux = new Aux();
		Word[] p = new Programas().fibonacci10;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 33);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 33);
	}

	// Programa que testa o programa progMinimo
	public void test2(){
		Aux aux = new Aux();
		Word[] p = new Programas().progMinimo;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 15);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 15);
	}

	// Programa que testa o programa fatorial
	public void test3(){
		Aux aux = new Aux();
		Word[] p = new Programas().fatorial;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 15);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 15);
	}

	// Programa que testa o programa testExercicio
	public void test4() {
		Aux aux = new Aux();
		Word[] p = new Programas().testExercicio;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 30);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, vm.tamMem);
	}

	// Programa que testa o programa testTrapInput
	public void test5() {
		Aux aux = new Aux();
		Word[] p = new Programas().testTrapHandlerInput;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 10);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 10);
	}

	// Programa que testa o programa testTrapOut
	public void test6() {
		Aux aux = new Aux();
		Word[] p = new Programas().testTrapHandlerOutput;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 10);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 10);
	}

	// Programa que testa o programa testInstructionsInvalid
	public void test7() {
		Aux aux = new Aux();
		Word[] p = new Programas().testInvalidInstructions;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 10);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 10);
	}

	// Programa que testa o programa testInstructionsOverflow
	public void test8() {
		Aux aux = new Aux();
		Word[] p = new Programas().testInstructionsOverflow;
		aux.carga(p, vm.m);
		vm.cpu.setContext(0, 0, vm.tamMem - 1);
		System.out.println("---------------------------------- programa carregado ");
		aux.dump(vm.m, 0, 10);
		System.out.println("---------------------------------- após execucao ");
		vm.cpu.run();
		aux.dump(vm.m, 0, 10);
	}

	// -------------------------------------------  classes e funcoes auxiliares
    public class Aux {
		public void dump(Word w) {
			System.out.print("[ "); 
			System.out.print(w.opc); System.out.print(", ");
			System.out.print(w.r1);  System.out.print(", ");
			System.out.print(w.r2);  System.out.print(", ");
			System.out.print(w.p);  System.out.println("  ] ");
		}
		public void dump(Word[] m, int ini, int fim) {
			for (int i = ini; i < fim; i++) {
				System.out.print(i); System.out.print(":  ");  dump(m[i]);
			}
		}
		public void carga(Word[] p, Word[] m) {
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
			new Word(Opcode.DATA, -1, -1, -1)  // ate aqui - serie de fibonacci ficara armazenada
		};   

		public Word[] fatorial = new Word[] { 	 // este fatorial so aceita valores positivos.   nao pode ser zero
												 // linha   coment
			new Word(Opcode.LDI, 0, -1, 125),      // 0   	r0 é valor a calcular fatorial
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
    }
}
