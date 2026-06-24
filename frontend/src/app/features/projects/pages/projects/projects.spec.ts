import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {Projects} from './projects';
import {ProjectsService} from '../../../../core/services/projects.service';
import {AuthService} from '../../../../core/services/auth.service';
import {provideI18n} from '../../../../../testing/provide-i18n';

describe('Projects component', () => {
  let fixture: ComponentFixture<Projects>;
  let component: Projects;
  let projectsService: {
    findPage: ReturnType<typeof vi.fn>;
    findAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    const project = {
      id: 1,
      name: 'Portal',
      description: 'Workspace',
      status: 'ACTIVE',
      ownerId: 1,
      ownerName: 'Admin Local',
      ownerEmail: 'admin@local.dev',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    projectsService = {
      findPage: vi.fn().mockReturnValue(of({items: [project], page: 0, size: 5, totalItems: 1, totalPages: 1})),
      findAll: vi.fn().mockReturnValue(of([project])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [Projects],
      providers: [
        provideI18n(),
        {provide: ProjectsService, useValue: projectsService},
        {
          provide: AuthService,
          useValue: {
            currentUser: () => ({id: 1, firstName: 'Admin', lastName: 'Local', email: 'admin@local.dev', role: 'ADMIN', emailVerified: true}),
            getCurrentUserRole: vi.fn().mockReturnValue('ADMIN')
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Projects);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('charge les projets au démarrage', () => {
    expect(projectsService.findPage).toHaveBeenCalledWith({page: 0, size: 5, sort: 'updatedAt,desc'});
    expect(component.projects().length).toBe(1);
  });

  it('crée un projet et le prepend dans la liste', () => {
    projectsService.create.mockReturnValue(of({
      id: 2,
      name: 'Boilerplate',
      description: 'New project',
      status: 'DRAFT',
      ownerId: 1,
      ownerName: 'Admin Local',
      ownerEmail: 'admin@local.dev',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }));

    component.startCreate();
    component.form.setValue({
      name: 'Boilerplate',
      description: 'New project',
      status: 'DRAFT'
    });

    component.submit();

    expect(projectsService.create).toHaveBeenCalledWith({
      name: 'Boilerplate',
      description: 'New project',
      status: 'DRAFT'
    });
    expect(component.projects()[0].id).toBe(2);
    expect(component.infoMessage()).toBe('Projet créé.');
  });
});
