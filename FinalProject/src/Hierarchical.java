import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.*;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import com.sun.opengl.util.BufferUtil;
import com.sun.opengl.util.FPSAnimator;
import com.sun.opengl.util.GLUT;

import java.util.ArrayList;
import java.math.*;
class Hierarchical extends JFrame implements GLEventListener, KeyListener, MouseListener, MouseMotionListener, ActionListener {

	/* This defines the objModel class, which takes care
	 * of loading a triangular mesh from an obj file,
	 * estimating per vertex average normal,
	 * and displaying the mesh.
	 */
	class objModel {
		public FloatBuffer vertexBuffer;
		public IntBuffer faceBuffer;
		public FloatBuffer normalBuffer;
		public Point3f center;
		public int num_verts;		// number of vertices
		public int num_faces;		// number of triangle faces

		public void Draw() {
			vertexBuffer.rewind();
			normalBuffer.rewind();
			faceBuffer.rewind();
			gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL.GL_NORMAL_ARRAY);
			
			gl.glVertexPointer(3, GL.GL_FLOAT, 0, vertexBuffer);
			gl.glNormalPointer(GL.GL_FLOAT, 0, normalBuffer);
			
			gl.glDrawElements(GL.GL_TRIANGLES, num_faces*3, GL.GL_UNSIGNED_INT, faceBuffer);
			
			gl.glDisableClientState(GL.GL_VERTEX_ARRAY);
			gl.glDisableClientState(GL.GL_NORMAL_ARRAY);
		}
		
		public objModel(String filename) {
			/* load a triangular mesh model from a .obj file */
			BufferedReader in = null;
			try {
				in = new BufferedReader(new FileReader(filename));
			} catch (IOException e) {
				System.out.println("Error reading from file " + filename);
				System.exit(0);
			}

			center = new Point3f();			
			float x, y, z;
			int v1, v2, v3;
			float minx, miny, minz;
			float maxx, maxy, maxz;
			float bbx, bby, bbz;
			minx = miny = minz = 10000.f;
			maxx = maxy = maxz = -10000.f;
			
			String line;
			String[] tokens;
			ArrayList<Point3f> input_verts = new ArrayList<Point3f> ();
			ArrayList<Integer> input_faces = new ArrayList<Integer> ();
			ArrayList<Vector3f> input_norms = new ArrayList<Vector3f> ();
			try {
			while ((line = in.readLine()) != null) {
				if (line.length() == 0)
					continue;
				switch(line.charAt(0)) {
				case 'v':
					tokens = line.split("[ ]+");
					x = Float.valueOf(tokens[1]);
					y = Float.valueOf(tokens[2]);
					z = Float.valueOf(tokens[3]);
					minx = Math.min(minx, x);
					miny = Math.min(miny, y);
					minz = Math.min(minz, z);
					maxx = Math.max(maxx, x);
					maxy = Math.max(maxy, y);
					maxz = Math.max(maxz, z);
					input_verts.add(new Point3f(x, y, z));
					center.add(new Point3f(x, y, z));
					break;
				case 'f':
					tokens = line.split("[ ]+");
					v1 = Integer.valueOf(tokens[1])-1;
					v2 = Integer.valueOf(tokens[2])-1;
					v3 = Integer.valueOf(tokens[3])-1;
					input_faces.add(v1);
					input_faces.add(v2);
					input_faces.add(v3);				
					break;
				default:
					continue;
				}
			}
			in.close();	
			} catch(IOException e) {
				System.out.println("Unhandled error while reading input file.");
			}

			System.out.println("Read " + input_verts.size() +
						   	" vertices and " + input_faces.size() + " faces.");
			
			center.scale(1.f / (float) input_verts.size());
			
			bbx = maxx - minx;
			bby = maxy - miny;
			bbz = maxz - minz;
			float bbmax = Math.max(bbx, Math.max(bby, bbz));
			
			for (Point3f p : input_verts) {
				
				p.x = (p.x - center.x) / bbmax;
				p.y = (p.y - center.y) / bbmax;
				p.z = (p.z - center.z) / bbmax;
			}
			center.x = center.y = center.z = 0.f;
			
			/* estimate per vertex average normal */
			int i;
			for (i = 0; i < input_verts.size(); i ++) {
				input_norms.add(new Vector3f());
			}
			
			Vector3f e1 = new Vector3f();
			Vector3f e2 = new Vector3f();
			Vector3f tn = new Vector3f();
			for (i = 0; i < input_faces.size(); i += 3) {
				v1 = input_faces.get(i+0);
				v2 = input_faces.get(i+1);
				v3 = input_faces.get(i+2);
				
				e1.sub(input_verts.get(v2), input_verts.get(v1));
				e2.sub(input_verts.get(v3), input_verts.get(v1));
				tn.cross(e1, e2);
				input_norms.get(v1).add(tn);
				
				e1.sub(input_verts.get(v3), input_verts.get(v2));
				e2.sub(input_verts.get(v1), input_verts.get(v2));
				tn.cross(e1, e2);
				input_norms.get(v2).add(tn);
				
				e1.sub(input_verts.get(v1), input_verts.get(v3));
				e2.sub(input_verts.get(v2), input_verts.get(v3));
				tn.cross(e1, e2);
				input_norms.get(v3).add(tn);			
			}

			/* convert to buffers to improve display speed */
			for (i = 0; i < input_verts.size(); i ++) {
				input_norms.get(i).normalize();
			}
			
			vertexBuffer = BufferUtil.newFloatBuffer(input_verts.size()*3);
			normalBuffer = BufferUtil.newFloatBuffer(input_verts.size()*3);
			faceBuffer = BufferUtil.newIntBuffer(input_faces.size());
			
			for (i = 0; i < input_verts.size(); i ++) {
				vertexBuffer.put(input_verts.get(i).x);
				vertexBuffer.put(input_verts.get(i).y);
				vertexBuffer.put(input_verts.get(i).z);
				normalBuffer.put(input_norms.get(i).x);
				normalBuffer.put(input_norms.get(i).y);
				normalBuffer.put(input_norms.get(i).z);			
			}
			
			for (i = 0; i < input_faces.size(); i ++) {
				faceBuffer.put(input_faces.get(i));	
			}			
			num_verts = input_verts.size();
			num_faces = input_faces.size()/3;
		}		
	}


	public void keyPressed(KeyEvent e) {
		switch(e.getKeyCode()) {
		case KeyEvent.VK_ESCAPE:
		case KeyEvent.VK_Q:
			System.exit(0);
			break;		
		case 'r':
		case 'R':
			initViewParameters();
			break;
		case 'w':
		case 'W':
			wireframe = ! wireframe;
			break;
		case 'b':
		case 'B':
			cullface = !cullface;
			break;
		case 'f':
		case 'F':
			flatshade = !flatshade;
			break;
		case 'a':
		case 'A':
			/*if (animator.isAnimating())
				animator.stop();
			else 
				animator.start();*/
			if(!animator.isAnimating())
				animator.start();
			else
				animator.stop();
			break;
		case '+':
		case '=':
			animation_speed *= 1.2f;
			break;
		case '-':
		case '_':
			animation_speed /= 1.2;
			break;
		default:
			break;
		}
		canvas.display();
	}
	
	/* GL, display, model transformation, and mouse control variables */
	private final GLCanvas canvas;
	private GL gl;
	private final GLU glu = new GLU();	
	private FPSAnimator animator;

	private int winW = 800, winH = 800;
	private boolean wireframe = false;
	private boolean cullface = true;
	private boolean flatshade = false;
	
	private float xpos = 0, ypos = 0, zpos = 0;
	private float centerx, centery, centerz;
	private float roth = 0, rotv = 0;
	private float znear, zfar;
	private int mouseX, mouseY, mouseButton;
	private float motionSpeed, rotateSpeed;
	private float animation_speed = 1.0f;
	
	
	
	/* === YOUR WORK HERE === */
	/* Define more models you need for constructing your scene */
	//private objModel example_model = new objModel("bunny.obj");
	private objModel model1 = new objModel("statue.obj");
	private objModel model2 = new objModel("male.obj");
	private objModel model3 = new objModel("male.obj");
	private objModel model4 = new objModel("male.obj");
	
	private objModel test1 = new objModel("bottle.obj");

	private float example_rotateT = 0.f;
	/* Here you should give a conservative estimate of the scene's bounding box
	 * so that the initViewParameters function can calculate proper
	 * transformation parameters to display the initial scene.
	 * If these are not set correctly, the objects may disappear on start.
	 */
	private float xmin = -1.5f, ymin = -1.5f, zmin = -1.5f;
	private float xmax = 1.5f, ymax = 1.5f, zmax = 1.5f;	
	
	
	public void display(GLAutoDrawable drawable) {
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, wireframe ? GL.GL_LINE : GL.GL_FILL);	
		gl.glShadeModel(flatshade ? GL.GL_FLAT : GL.GL_SMOOTH);		
		if (cullface)
			gl.glEnable(GL.GL_CULL_FACE);
		else
			gl.glDisable(GL.GL_CULL_FACE);		
		
		gl.glLoadIdentity();
		
		/* this is the transformation of the entire scene */
		gl.glTranslatef(-xpos, -ypos, -zpos);
		gl.glTranslatef(centerx, centery, centerz);
		gl.glRotatef(360.f - roth, 0, 1.0f, 0);
		gl.glRotatef(rotv, 1.0f, 0, 0);
		gl.glTranslatef(-centerx, -centery, -centerz);	

		
		/* === YOUR WORK HERE === */
		
		/* Below is an example of a rotating bunny
		 * It rotates the bunny with example_rotateT degrees around the bunny's gravity center  
		 */
		
		/*gl.glTranslatef(example_model.center.x, example_model.center.y, example_model.center.z);
		gl.glRotatef(example_rotateT, 0, example_model.center.y, 1);
		gl.glTranslatef(-example_model.center.x, -example_model.center.y, -example_model.center.z);
		/* call objModel::Draw function to draw the model 
		example_model.Draw();*/
		
		gl.glPushMatrix();	// push the current matrix to stack
		
		//center (lvl 1)
		gl.glScalef((float).5, (float).5, (float).5);
		gl.glRotatef(example_rotateT, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, test1.center.z);
		test1.Draw();
		
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		
		//lvl 2 left
		gl.glScalef((float).5, (float).5, (float).5);
		gl.glRotatef((float) (example_rotateT * .5), 0, 1, 0);
		gl.glTranslatef(test1.center.x - 2, test1.center.y, test1.center.z);
		test1.Draw();
		
		//lvl 3 left
		gl.glRotatef(example_rotateT, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x - 1), test1.center.y, test1.center.z);
		test1.Draw();
		
		//lvl 4 left
		gl.glScalef((float).4, (float).4, (float).4);
		gl.glRotatef(90, 0, 0, 1);
		gl.glRotatef((float) (example_rotateT * 1.5), 1, 0, 0);
		gl.glTranslatef((float) (test1.center.x - .75), (float) (test1.center.y - 1), test1.center.z);
		test1.Draw();
		
		//lvl 5 left front
		gl.glPushMatrix();
				
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z + .5));
		test1.Draw();
						
		gl.glPopMatrix();
						
		gl.glPushMatrix();
						
		//lvl 5 left back
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z - .5));
		test1.Draw();
						
		gl.glPopMatrix();
		
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		
		//lvl 2 right
		gl.glScalef((float).5, (float).5, (float).5);
		gl.glRotatef((float) (example_rotateT * .5), 0, 1, 0);
		gl.glTranslatef(test1.center.x + 2, test1.center.y, test1.center.z);
		test1.Draw();
		
		//lvl 3 right
		gl.glRotatef(example_rotateT, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x + 1), test1.center.y, test1.center.z);
		test1.Draw();
		
		//lvl 4 right
		gl.glScalef((float).4, (float).4, (float).4);
		gl.glRotatef(270, 0, 0, 1);
		gl.glRotatef((float) (example_rotateT * 1.5), -1, 0, 0);
		gl.glTranslatef((float) (test1.center.x + .75), (float) (test1.center.y - 1), test1.center.z);
		test1.Draw();
		
		//lvl 5 right front
		gl.glPushMatrix();
		
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z + .5));
		test1.Draw();
				
		gl.glPopMatrix();
				
		gl.glPushMatrix();
				
		//lvl 5 right back
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z - .5));
		test1.Draw();
				
		gl.glPopMatrix();
		
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		
		//lvl 2 front
		gl.glScalef((float).5, (float).5, (float).5);
		gl.glRotatef((float) (example_rotateT * .5), 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, test1.center.z + 2);
		test1.Draw();
				
		//lvl 3 front
		gl.glRotatef(example_rotateT, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z + 1));
		test1.Draw();
				
		//lvl 4 front
		gl.glScalef((float).4, (float).4, (float).4);
		gl.glRotatef(90, 1, 0, 0);
		gl.glRotatef((float) (example_rotateT * 1.5), 0, 0, -1);
		gl.glTranslatef((float) (test1.center.x), (float) (test1.center.y - 1), (float) (test1.center.z + .75));
		test1.Draw();
		
		//lvl 5 front left
		gl.glPushMatrix();
		
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x - .5), test1.center.y, test1.center.z);
		test1.Draw();
		
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		
		//lvl 5 front right
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x + .5), test1.center.y, test1.center.z);
		test1.Draw();
		
		gl.glPopMatrix();
				
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		
		//lvl 2 back
		gl.glScalef((float).5, (float).5, (float).5);
		gl.glRotatef((float) (example_rotateT * .5), 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, test1.center.z - 2);
		test1.Draw();
				
		//lvl 3 back
		gl.glRotatef(example_rotateT, 0, 1, 0);
		gl.glTranslatef(test1.center.x, test1.center.y, (float) (test1.center.z - 1));
		test1.Draw();
				
		//lvl 4 back
		gl.glScalef((float).4, (float).4, (float).4);
		gl.glRotatef(270, 1, 0, 0);
		gl.glRotatef((float) (example_rotateT * 1.5), 0, 0, 1);
		gl.glTranslatef(test1.center.x, (float) (test1.center.y - 1), (float) (test1.center.z - .75));
		test1.Draw();
				
		//lvl 5 back left
		gl.glPushMatrix();
				
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x - .5), test1.center.y, test1.center.z);
		test1.Draw();
				
		gl.glPopMatrix();
				
		gl.glPushMatrix();
				
		//lvl 5 back right
		gl.glRotatef(example_rotateT * 2, 0, 1, 0);
		gl.glTranslatef((float) (test1.center.x + .5), test1.center.y, test1.center.z);
		test1.Draw();
				
		gl.glPopMatrix();
				
		gl.glPopMatrix();
		
		
		
		/* increment example_rotateT */
		if (animator.isAnimating())
			example_rotateT += 1.0f * animation_speed;
	}	
	
	public Hierarchical() {
		super("Assignment 3 -- Hierarchical Modeling");
		canvas = new GLCanvas();
		canvas.addGLEventListener(this);
		canvas.addKeyListener(this);
		canvas.addMouseListener(this);
		canvas.addMouseMotionListener(this);
		animator = new FPSAnimator(canvas, 30);	// create a 30 fps animator
		getContentPane().add(canvas);
		setSize(winW, winH);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		//animator.start();
		canvas.requestFocus();
	}
	
	public static void main(String[] args) {

		new Hierarchical();
	}
	
	public void init(GLAutoDrawable drawable) {
		gl = drawable.getGL();

		initViewParameters();
		gl.glClearColor(.1f, .1f, .1f, 1f);
		gl.glClearDepth(1.0f);

	    // white light at the eye
	    float light0_position[] = { 0, 0, 1, 0 };
	    float light0_diffuse[] = { 1, 1, 1, 1 };
	    float light0_specular[] = { 1, 1, 1, 1 };
	    gl.glLightfv( GL.GL_LIGHT0, GL.GL_POSITION, light0_position, 0);
	    gl.glLightfv( GL.GL_LIGHT0, GL.GL_DIFFUSE, light0_diffuse, 0);
	    gl.glLightfv( GL.GL_LIGHT0, GL.GL_SPECULAR, light0_specular, 0);

	    //red light
	    float light1_position[] = { -.1f, .1f, 0, 0 };
	    float light1_diffuse[] = { .6f, .05f, .05f, 1 };
	    float light1_specular[] = { .6f, .05f, .05f, 1 };
	    gl.glLightfv( GL.GL_LIGHT1, GL.GL_POSITION, light1_position, 0);
	    gl.glLightfv( GL.GL_LIGHT1, GL.GL_DIFFUSE, light1_diffuse, 0);
	    gl.glLightfv( GL.GL_LIGHT1, GL.GL_SPECULAR, light1_specular, 0);

	    //blue light
	    float light2_position[] = { .1f, .1f, 0, 0 };
	    float light2_diffuse[] = { .05f, .05f, .6f, 1 };
	    float light2_specular[] = { .05f, .05f, .6f, 1 };
	    gl.glLightfv( GL.GL_LIGHT2, GL.GL_POSITION, light2_position, 0);
	    gl.glLightfv( GL.GL_LIGHT2, GL.GL_DIFFUSE, light2_diffuse, 0);
	    gl.glLightfv( GL.GL_LIGHT2, GL.GL_SPECULAR, light2_specular, 0);

	    //material
	    float mat_ambient[] = { 0, 0, 0, 1 };
	    float mat_specular[] = { .8f, .8f, .8f, 1 };
	    float mat_diffuse[] = { .4f, .4f, .4f, 1 };
	    float mat_shininess[] = { 128 };
	    gl.glMaterialfv( GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient, 0);
	    gl.glMaterialfv( GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
	    gl.glMaterialfv( GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
	    gl.glMaterialfv( GL.GL_FRONT, GL.GL_SHININESS, mat_shininess, 0);

	    float bmat_ambient[] = { 0, 0, 0, 1 };
	    float bmat_specular[] = { 0, .8f, .8f, 1 };
	    float bmat_diffuse[] = { 0, .4f, .4f, 1 };
	    float bmat_shininess[] = { 128 };
	    gl.glMaterialfv( GL.GL_BACK, GL.GL_AMBIENT, bmat_ambient, 0);
	    gl.glMaterialfv( GL.GL_BACK, GL.GL_SPECULAR, bmat_specular, 0);
	    gl.glMaterialfv( GL.GL_BACK, GL.GL_DIFFUSE, bmat_diffuse, 0);
	    gl.glMaterialfv( GL.GL_BACK, GL.GL_SHININESS, bmat_shininess, 0);

	    float lmodel_ambient[] = { 0, 0, 0, 1 };
	    gl.glLightModelfv( GL.GL_LIGHT_MODEL_AMBIENT, lmodel_ambient, 0);
	    gl.glLightModeli( GL.GL_LIGHT_MODEL_TWO_SIDE, 1 );

	    gl.glEnable( GL.GL_NORMALIZE );
	    gl.glEnable( GL.GL_LIGHTING );
	    gl.glEnable( GL.GL_LIGHT0 );
	    gl.glEnable( GL.GL_LIGHT1 );
	    gl.glEnable( GL.GL_LIGHT2 );

	    gl.glEnable(GL.GL_DEPTH_TEST);
		gl.glDepthFunc(GL.GL_LESS);
		gl.glHint(GL.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);
		gl.glCullFace(GL.GL_BACK);
		gl.glEnable(GL.GL_CULL_FACE);
		gl.glShadeModel(GL.GL_SMOOTH);		
	}
	
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		winW = width;
		winH = height;

		gl.glViewport(0, 0, width, height);
		gl.glMatrixMode(GL.GL_PROJECTION);
			gl.glLoadIdentity();
			glu.gluPerspective(45.f, (float)width/(float)height, znear, zfar);
		gl.glMatrixMode(GL.GL_MODELVIEW);
	}
	
	public void mousePressed(MouseEvent e) {	
		mouseX = e.getX();
		mouseY = e.getY();
		mouseButton = e.getButton();
		canvas.display();
	}
	
	public void mouseReleased(MouseEvent e) {
		mouseButton = MouseEvent.NOBUTTON;
		canvas.display();
	}	
	
	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		if (mouseButton == MouseEvent.BUTTON3) {
			zpos -= (y - mouseY) * motionSpeed;
			mouseX = x;
			mouseY = y;
			canvas.display();
		} else if (mouseButton == MouseEvent.BUTTON2) {
			xpos -= (x - mouseX) * motionSpeed;
			ypos += (y - mouseY) * motionSpeed;
			mouseX = x;
			mouseY = y;
			canvas.display();
		} else if (mouseButton == MouseEvent.BUTTON1) {
			roth -= (x - mouseX) * rotateSpeed;
			rotv += (y - mouseY) * rotateSpeed;
			mouseX = x;
			mouseY = y;
			canvas.display();
		}
	}

	
	/* computes optimal transformation parameters for OpenGL rendering.
	 * this is based on an estimate of the scene's bounding box
	 */	
	void initViewParameters()
	{
		roth = rotv = 0;

		float ball_r = (float) Math.sqrt((xmax-xmin)*(xmax-xmin)
							+ (ymax-ymin)*(ymax-ymin)
							+ (zmax-zmin)*(zmax-zmin)) * 0.707f;

		centerx = (xmax+xmin)/2.f;
		centery = (ymax+ymin)/2.f;
		centerz = (zmax+zmin)/2.f;
		xpos = centerx;
		ypos = centery;
		zpos = ball_r/(float) Math.sin(45.f*Math.PI/180.f)+centerz;

		znear = 0.01f;
		zfar  = 1000.f;

		motionSpeed = 0.002f * ball_r;
		rotateSpeed = 0.1f;

	}	
	
	// these event functions are not used for this assignment
	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) { }
	public void keyTyped(KeyEvent e) { }
	public void keyReleased(KeyEvent e) { }
	public void mouseMoved(MouseEvent e) { }
	public void actionPerformed(ActionEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) {	}	
}
