package lua.addon.luacompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import lua.CallInfo;
import lua.GlobalState;
import lua.Lua;
import lua.StackState;
import lua.VM;
import lua.io.Closure;
import lua.io.LoadState;
import lua.io.Proto;
import lua.value.LDouble;
import lua.value.LFunction;
import lua.value.LInteger;
import lua.value.LNil;
import lua.value.LNumber;
import lua.value.LString;
import lua.value.LTable;
import lua.value.LValue;

public class LuaCompat extends LFunction {

	public static InputStream STDIN = null;
	public static PrintStream STDOUT = System.out;
	
	public static void install() {
		LTable globals = GlobalState.getGlobalsTable();
		for ( int i = 0; i < GLOBAL_NAMES.length; ++i ) {
			globals.put( GLOBAL_NAMES[i], new LuaCompat( i ) );
		}
		
		LTable math = new LTable();
		for ( int i = 0; i < MATH_NAMES.length; ++i ) {
			math.put( MATH_NAMES[i], new LuaCompat( MATH_BASE + i ) );
		}
		
		// Some handy constants
		math.put( "huge", new LDouble( Double.MAX_VALUE ) );
		math.put( "pi", new LDouble( Math.PI ) );
		
		globals.put( "math", math );
		
		LTable string = LString.getMetatable();
		for ( int i = 0; i < STRING_NAMES.length; ++i ) {
			string.put( STRING_NAMES[i], new LuaCompat( STRING_BASE + i ) );
		}
		globals.put( "string", string );
	}
	
	public static final String[] GLOBAL_NAMES = {
		"assert",
		"loadfile",
		"tonumber",
		"rawget",
		"setfenv",
		"select",
		"collectgarbage",
		"dofile",
		"loadstring",
		"load",
		"tostring",
		"unpack",
		"next",
	};
	
	public static final String[] MATH_NAMES = {
		"abs",
		"max",
		"min",
		"modf",
		"sin"
	};
	
	public static final String[] STRING_NAMES = {
		"rep",
		"sub",
	};
	
	private static final int ASSERT = 0;
	private static final int LOADFILE = 1;
	private static final int TONUMBER = 2;
	private static final int RAWGET = 3;
	private static final int SETFENV = 4;
	private static final int SELECT = 5;
	private static final int COLLECTGARBAGE = 6;
	private static final int DOFILE = 7;
	private static final int LOADSTRING = 8;
	private static final int LOAD = 9;
	private static final int TOSTRING = 10;
	private static final int UNPACK= 11;
	private static final int NEXT= 12;
	
	
	private static final int MATH_BASE = 20;
	private static final int ABS = MATH_BASE + 0;
	private static final int MAX = MATH_BASE + 1;
	private static final int MIN = MATH_BASE + 2;
	private static final int MODF = MATH_BASE + 3;
	private static final int SIN = MATH_BASE + 4;
	
	private static final int STRING_BASE = 30;
	private static final int REP = STRING_BASE + 0;
	private static final int SUB = STRING_BASE + 1;
	
	private final int id;
	private LuaCompat( int id ) {
		this.id = id;
	}
	
	public boolean luaStackCall( VM vm ) {
		switch ( id ) {
		case ASSERT: {
			if ( !vm.getArgAsBoolean(0) ) {
				String message;
				if ( vm.getArgCount() > 1 ) {
					message = vm.getArgAsString(1);
				} else {
					message = "assertion failed!";
				}
				throw new RuntimeException(message);
			}
			vm.setResult();
		}	break;
		case LOADFILE:
			vm.setResult( loadfile(vm, vm.getArg(0)) );
			break;
		case TONUMBER:
			vm.setResult( toNumber( vm ) );
			break;
		case RAWGET: {
			LValue t = vm.getArg(0);;
			LValue k = vm.getArg(1);
			LValue result = LNil.NIL;
			if ( t instanceof LTable ) {
				result = ( (LTable) t ).get( k );
			}
			vm.setResult( result );
		}	break;
		case SETFENV:
			setfenv( (StackState) vm );
			break;
		case SELECT:
			select( vm );
			break;
		case COLLECTGARBAGE:
			System.gc();
			vm.setResult();
			break;
		case DOFILE:
			dofile(vm, vm.getArg(0));
			break;
		case LOADSTRING:
			vm.setResult( loadstring(vm, vm.getArg(0), vm.getArgAsString(1)) );
			break;
		case LOAD:
			vm.setResult( load(vm, vm.getArg(0), vm.getArgAsString(1)) );
			break;
		case TOSTRING:
			vm.setResult( tostring(vm, vm.getArg(0)) );
			break;
		case UNPACK:
			vm.setResult();
			unpack(vm, vm.getArg(0), vm.getArgAsInt(1), vm.getArgAsInt(2));
			break;
		case NEXT:
			vm.setResult( next(vm, vm.getArg(0), vm.getArgAsInt(1)) );
			break;
		
		// Math functions
		case ABS:
			vm.setResult( abs( vm.getArg( 0 ) ) );
			break;
		case MAX:
			vm.setResult( max( vm.getArg( 0 ), vm.getArg( 1 ) ) );
			break;
		case MIN:
			vm.setResult( min( vm.getArg( 0 ), vm.getArg( 1 ) ) );
			break;
		case MODF:
			modf( vm );
			break;
		case SIN:
			vm.setResult( new LDouble( Math.sin( vm.getArgAsDouble( 0 ) ) ) );
			break;
		
		// String functions
		case REP: {
			LString s = vm.getArgAsLuaString( 0 );
			int n = vm.getArgAsInt( 1 );
			if ( n >= 0 ) {
				final byte[] bytes = new byte[ s.length() * n ];
				int len = s.length();
				for ( int offset = 0; offset < bytes.length; offset += len ) {
					s.copyInto( 0, bytes, offset, len );
				}
				
				vm.setResult( new LString( bytes ) );
			} else {
				vm.setResult( LNil.NIL );
			}
		}	break;
		case SUB: {
			final LString s = vm.getArgAsLuaString( 0 );
			final int len = s.length();
			
			int i = vm.getArgAsInt( 1 );
			if ( i < 0 ) {
				// start at -i characters from the end
				i = Math.max( len + i, 0 );
			} else if ( i > 0 ) {
				// start at character i - 1
				i = i - 1;
			}
			
			int j = ( vm.getArgCount() > 2 ) ? vm.getArgAsInt( 2 ): -1;
			if ( j < 0 ) {
				j = Math.max( i, len + j + 1 );
			} else {
				j = Math.min( Math.max( i, j ), len );
			}
			
			LString result = s.substring( i, j );
			vm.setResult( result );
		}	break;
		
		default:
			luaUnsupportedOperation();
		}
		return false;
	}
	private void select( VM vm ) {
		LValue arg = vm.getArg( 0 );
		if ( arg instanceof LNumber ) {
			final int start;
			final int numResults;
			if ( ( start = arg.luaAsInt() ) > 0 &&
				 ( numResults = Math.max( vm.getArgCount() - start,
						 				  vm.getExpectedResultCount() ) ) > 0 ) {
				// since setResult trashes the arguments, we have to save them somewhere.
				LValue[] results = new LValue[numResults];
				for ( int i = 0; i < numResults; ++i ) {
					results[i] = vm.getArg( i+start );
				}
				vm.setResult();
				for ( int i = 0; i < numResults; ++i ) {
					vm.push( results[i] );
				}
				return;
			}
		} else if ( arg.luaAsString().equals( "#" ) ) {
			vm.setResult( new LInteger( vm.getArgCount() - 1 ) );
		}
		vm.setResult();
	}
	
	private LValue abs( final LValue v ) {
		LValue nv = v.luaUnaryMinus();
		return max( v, nv );
	}
	
	private LValue max( LValue lhs, LValue rhs ) {
		return rhs.luaBinCmpUnknown( Lua.OP_LT, lhs ) ? rhs: lhs;
	}
	
	private LValue min( LValue lhs, LValue rhs ) {
		return rhs.luaBinCmpUnknown( Lua.OP_LT, lhs ) ? lhs: rhs;
	}
	
	private void modf( VM vm ) {
		LValue arg = vm.getArg( 0 );
		double v = arg.luaAsDouble();
		double intPart = ( v > 0 ) ? Math.floor( v ) : Math.ceil( v );
		double fracPart = v - intPart;
		vm.setResult();
		vm.push( intPart );
		vm.push( fracPart );
	}
	
	private LValue toNumber( VM vm ) {
		LValue input = vm.getArg(0);
		if ( input instanceof LNumber ) {
			return input;
		} else if ( input instanceof LString ) {
			int base = 10;
			if ( vm.getArgCount()>1 ) {
				base = vm.getArgAsInt(1);
			}
			return ( (LString) input ).luaToNumber( base );
		}
		return LNil.NIL;
	}
	
	private void setfenv( StackState state ) {
		LValue f = state.getArg(0);
		LValue newenv = state.getArg(1);

		Closure c = null;
		
		// Lua reference manual says that first argument, f, can be a "Lua
		// function" or an integer. Lots of things extend LFunction, but only
		// instances of Closure are "Lua functions".
		if ( f instanceof Closure ) {
			c = (Closure) f;
		} else {
			int callStackDepth = f.luaAsInt();
			if ( callStackDepth > 0 ) {
				CallInfo frame = state.getStackFrame( callStackDepth );
				if ( frame != null ) {
					c = frame.closure;
				}
			} else {
				// This is supposed to set the environment of the current
				// "thread". But, we have not implemented coroutines yet.
				throw new RuntimeException( "not implemented" );
			}
		}
		
		if ( c != null ) {
			if ( newenv instanceof LTable ) {
				c.env = (LTable) newenv;
			}
			state.setResult( c );
			return;
		}
		
		state.setResult();
		return;
	}

	// closes the input stream, provided its not null or System.in
	private static void closeSafely(InputStream is) {
		try {
			if ( is != null && is != STDIN )
				is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// closes the output stream, provided its not null or STDOUT 
	private static void closeSafely(OutputStream os) {
		try {
			if ( os != null && os != STDOUT )
				os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// closes the input stream
	private static LValue inputStreamToClosureThenClose(VM vm, InputStream is, String chunkname ) {
		try {
			Proto p = LoadState.undump(vm, is, chunkname);
			return new Closure( (StackState) vm, p);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			closeSafely( is );
		}
		return LNil.NIL;
	}
	

	private LValue loadfile( VM vm, LValue fileName ) {
		InputStream is;
		
		String script;
		if ( fileName != null ) {
			script = fileName.luaAsString().toJavaString();
			is = getClass().getResourceAsStream( "/"+script );
		} else {
			is = STDIN;
			script = "-";
		}
		
		if ( is != null )
			return inputStreamToClosureThenClose( vm, is, script );
		return LNil.NIL;
	}
	
	private LValue dofile( VM vm, LValue fileName ) {
		LValue chunk = loadfile( vm, fileName );
		if ( chunk != LNil.NIL ) {
			vm.doCall((Closure) chunk, null); 
		} else {
			vm.setResult();
			vm.push("file not found: "+fileName);
			vm.lua_error();
		}
		return null;
	}

	private LValue loadstring(VM vm,  LValue string, String chunkname) {
		InputStream is = string.luaAsString().toInputStream();
		return inputStreamToClosureThenClose( vm, is, chunkname );
	}

	// TODO: convert this to stream? 
	private LValue load(VM vm, LValue chunkPartLoader, String chunkname) {
		if ( ! (chunkPartLoader instanceof Closure) ) {
			vm.setResult();
			vm.push("not a function: "+chunkPartLoader);
			vm.lua_error();
		}
		
		// load all the parts
		Closure c = (Closure) chunkPartLoader;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			while ( true ) {
				vm.doCall( c, null );
				LValue r = vm.getArg(0);
				if ( ! (r instanceof LString) )
					break;
				LString s = (LString) r;
				s.write(baos, 0, s.length());
			}
		} catch ( Exception e ) {
			e.printStackTrace();
		} finally {
			closeSafely( baos );
		}

		// load from the byte array
		InputStream is = new ByteArrayInputStream( baos.toByteArray() );
		return inputStreamToClosureThenClose( vm, is, chunkname );
	}

	private LValue tostring(VM vm, LValue arg) {
		return arg.luaAsString();
	}

	private static LTable toTable(VM vm, LValue list) {
		if ( ! (list instanceof LTable) ) {
			vm.setResult();
			vm.push("not a list: "+list);
			vm.lua_error();
			return null;
		}
		return (LTable) list;
	}
	
	private void unpack(VM vm, LValue list, int i, int j) {
		LTable t = toTable(vm, list);
		if ( t == null )
			return; 
		if ( i == 0 )
			i = 1;
		if ( j == 0 )
			j = t.size();
		LValue[] keys = t.getKeys();
		for ( int k=i; k<=j; k++ )
			vm.push( t.get(keys[k-1]) );
	}

	private LValue next(VM vm, LValue table, int index) {
		throw new java.lang.RuntimeException("next() not supported yet");
	}

}
