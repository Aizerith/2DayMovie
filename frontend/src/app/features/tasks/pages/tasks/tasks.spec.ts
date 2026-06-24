import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {Tasks} from './tasks';
import {TasksService} from '../../../../core/services/tasks.service';
import {ProjectsService} from '../../../../core/services/projects.service';
import {UsersService} from '../../../../core/services/users.service';
import {provideI18n} from '../../../../../testing/provide-i18n';

describe('Tasks component', () => {
  let fixture: ComponentFixture<Tasks>;
  let component: Tasks;
  let tasksService: {
    findPage: ReturnType<typeof vi.fn>;
    findAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let projectsService: {
    findAll: ReturnType<typeof vi.fn>;
  };
  let usersService: {
    findAssignable: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    const task = {
      id: 1,
      projectId: 10,
      projectName: 'Portal',
      ownerId: 1,
      ownerName: 'Admin Local',
      assigneeId: 2,
      assigneeName: 'Manager Local',
      assigneeEmail: 'manager@local.dev',
      title: 'Build page',
      description: 'Implement CRUD',
      status: 'TODO',
      priority: 'MEDIUM',
      dueDate: null,
      manageable: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    tasksService = {
      findPage: vi.fn().mockReturnValue(of({items: [task], page: 0, size: 5, totalItems: 1, totalPages: 1})),
      findAll: vi.fn().mockReturnValue(of([task])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn()
    };

    projectsService = {
      findAll: vi.fn().mockReturnValue(of([
        {
          id: 10,
          name: 'Portal',
          description: 'Workspace',
          status: 'ACTIVE',
          ownerId: 1,
          ownerName: 'Admin Local',
          ownerEmail: 'admin@local.dev',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }
      ]))
    };

    usersService = {
      findAssignable: vi.fn().mockReturnValue(of([
        {
          id: 2,
          email: 'manager@local.dev',
          fullName: 'Manager Local',
          role: 'MANAGER'
        }
      ]))
    };

    await TestBed.configureTestingModule({
      imports: [Tasks],
      providers: [
        provideI18n(),
        {provide: TasksService, useValue: tasksService},
        {provide: ProjectsService, useValue: projectsService},
        {provide: UsersService, useValue: usersService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Tasks);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('charge projets et tâches au démarrage', () => {
    expect(projectsService.findAll).toHaveBeenCalled();
    expect(usersService.findAssignable).toHaveBeenCalled();
    expect(tasksService.findPage).toHaveBeenCalledWith({page: 0, size: 5, sort: 'dueDate,asc'}, null);
    expect(component.tasks().length).toBe(1);
  });

  it('préremplit le projet si un seul projet est disponible', () => {
    expect(component.form.controls.projectId.value).toBe(10);
  });

  it('crée une tâche avec payload normalisé', () => {
    tasksService.create.mockReturnValue(of({
      id: 2,
      projectId: 10,
      projectName: 'Portal',
      ownerId: 1,
      ownerName: 'Admin Local',
      assigneeId: 2,
      assigneeName: 'Manager Local',
      assigneeEmail: 'manager@local.dev',
      title: 'Ship feature',
      description: 'Final validation',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      dueDate: '2026-04-20',
      manageable: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }));

    component.startCreate();
    component.form.setValue({
      projectId: 10,
      assigneeId: 2,
      title: '  Ship feature  ',
      description: '  Final validation  ',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      dueDate: '2026-04-20'
    });

    component.submit();

    expect(tasksService.create).toHaveBeenCalledWith({
      projectId: 10,
      assigneeId: 2,
      title: 'Ship feature',
      description: 'Final validation',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      dueDate: '2026-04-20'
    });
    expect(component.infoMessage()).toBe('Tâche créée.');
  });

  it('bloque la mise à jour d’une tâche visible mais non gérable', () => {
    component.tasks.set([{
      id: 3,
      projectId: 10,
      projectName: 'Portal',
      ownerId: 1,
      ownerName: 'Admin Local',
      assigneeId: 7,
      assigneeName: 'Viewer Local',
      assigneeEmail: 'viewer@local.dev',
      title: 'Assigned review',
      description: 'Read only',
      status: 'TODO',
      priority: 'LOW',
      dueDate: null,
      manageable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }]);

    component.selectRow({id: '3'} as never);
    component.submit();

    expect(component.errorMessage()).toContain('seul le propriétaire du projet');
    expect(tasksService.update).not.toHaveBeenCalled();
  });
});
